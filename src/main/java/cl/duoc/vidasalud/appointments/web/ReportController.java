package cl.duoc.vidasalud.appointments.web;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.vidasalud.appointments.domain.Appointment;
import cl.duoc.vidasalud.appointments.domain.AppointmentStatus;
import cl.duoc.vidasalud.appointments.repo.AppointmentRepository;

/**
 * KPIs de solo lectura.
 *
 * En EP1 las agregaciones se calculan directamente sobre la tabla. En EP2 este
 * controlador se mueve a ms-vidasalud-report, que alimenta sus propias tablas
 * consumiendo el topico Kafka appointments.events.
 */
@RestController
@RequestMapping("/api/report")
public class ReportController {

    private final AppointmentRepository repository;

    public ReportController(AppointmentRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/kpis")
    public Map<String, Object> kpis(@RequestParam(defaultValue = "last24h") String range) {
        Instant since = Instant.now().minus(Duration.ofHours(parseHours(range)));
        List<Appointment> all = repository.findAll().stream()
                .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(since))
                .toList();

        long active = all.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.CONFIRMADA
                        || a.getStatus() == AppointmentStatus.EN_ESPERA
                        || a.getStatus() == AppointmentStatus.EN_ATENCION)
                .count();

        double avgWait = all.stream()
                .filter(a -> a.getUpdatedAt() != null)
                .mapToLong(a -> Duration.between(a.getCreatedAt(), a.getUpdatedAt()).toMinutes())
                .average().orElse(0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("range", range);
        result.put("total", all.size());
        result.put("active", active);
        result.put("closed", all.stream().filter(a -> a.getStatus() == AppointmentStatus.CERRADA).count());
        result.put("cancelled", all.stream().filter(a -> a.getStatus() == AppointmentStatus.CANCELADA).count());
        result.put("avgWaitMinutes", Math.round(avgWait));
        return result;
    }

    @GetMapping("/top-services")
    public List<Map<String, Object>> topServices(@RequestParam(defaultValue = "last7d") String range) {
        Instant since = Instant.now().minus(Duration.ofHours(parseHours(range)));
        return repository.findAll().stream()
                .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(since))
                .collect(Collectors.groupingBy(Appointment::getServiceId, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .map(e -> Map.<String, Object>of("serviceId", e.getKey(), "count", e.getValue()))
                .toList();
    }

    private long parseHours(String range) {
        if (range == null) {
            return 24;
        }
        if (range.endsWith("d")) {
            return Long.parseLong(range.replaceAll("[^0-9]", "")) * 24;
        }
        String digits = range.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? 24 : Long.parseLong(digits);
    }
}
