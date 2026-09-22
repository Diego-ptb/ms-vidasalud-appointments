package cl.duoc.vidasalud.appointments.web;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.vidasalud.appointments.repo.AppointmentRepository;

/**
 * Timeline de solo lectura.
 *
 * En EP1 se deriva del estado actual de cada atencion. En EP2 este controlador
 * se mueve a ms-vidasalud-audit, que persiste los eventos consumidos del topico
 * Kafka audit.timeline.
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AppointmentRepository repository;

    public AuditController(AppointmentRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/timeline")
    public List<Map<String, Object>> timeline() {
        AtomicLong seq = new AtomicLong(1);
        return repository.findAll().stream()
                .map(a -> Map.<String, Object>of(
                        "id", seq.getAndIncrement(),
                        "appointmentId", a.getId(),
                        "eventType", "APPOINTMENT_" + a.getStatus(),
                        "actor", a.getCreatedBy() == null ? "sistema" : a.getCreatedBy(),
                        "occurredAt", a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt()))
                .toList();
    }
}
