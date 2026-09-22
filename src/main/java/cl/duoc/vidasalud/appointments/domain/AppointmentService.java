package cl.duoc.vidasalud.appointments.domain;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import cl.duoc.vidasalud.appointments.repo.AppointmentRepository;
import cl.duoc.vidasalud.appointments.repo.AppointmentSpecs;
import cl.duoc.vidasalud.appointments.web.AppointmentDtos.CreateRequest;

@Service
public class AppointmentService {

    private final AppointmentRepository repository;

    public AppointmentService(AppointmentRepository repository) {
        this.repository = repository;
    }

    public List<Appointment> search(AppointmentStatus status, Instant from, Instant to) {
        return repository.findAll(AppointmentSpecs.search(status, from, to));
    }

    /** Atenciones de un unico paciente, identificado por su email del token. */
    public List<Appointment> searchDelPaciente(String patientEmail, AppointmentStatus status,
                                               Instant from, Instant to) {
        if (patientEmail == null || patientEmail.isBlank()) {
            // Sin identidad no se devuelve nada: es mas seguro que devolver todo.
            return List.of();
        }
        return repository.findAll(AppointmentSpecs.search(patientEmail, status, from, to));
    }

    public Appointment get(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "No existe la atencion " + id));
    }

    @Transactional
    public Appointment create(CreateRequest request, String createdBy) {
        // Regla de negocio, revalidada aqui y no solo con @Future en el DTO:
        // el servicio debe sostenerla venga la llamada de donde venga.
        if (!request.scheduledAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La atencion debe agendarse en una fecha y hora futura.");
        }

        Appointment appointment = new Appointment();
        appointment.setPatientName(request.patientName());
        appointment.setPatientEmail(request.patientEmail());
        appointment.setServiceId(request.serviceId());
        appointment.setScheduledAt(request.scheduledAt());
        appointment.setStatus(AppointmentStatus.SOLICITADA);
        appointment.setCreatedBy(createdBy);
        appointment.setCreatedAt(Instant.now());
        return repository.save(appointment);
    }

    /**
     * Cambia el estado respetando la maquina de estados. Un salto invalido
     * (por ejemplo SOLICITADA -> EN_ATENCION) responde 409 Conflict.
     */
    @Transactional
    public Appointment changeStatus(Long id, AppointmentStatus target) {
        Appointment appointment = get(id);
        AppointmentStatus current = appointment.getStatus();

        if (!current.canMoveTo(target)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Transicion invalida " + current + " -> " + target
                            + ". Permitidas: " + current.allowedNext());
        }

        appointment.setStatus(target);
        appointment.setUpdatedAt(Instant.now());

        // Al confirmar se asigna el box; aqui es donde EP2 descuenta el cupo
        // en ms-vidasalud-catalog y publica el evento a RabbitMQ / Kafka.
        if (target == AppointmentStatus.CONFIRMADA && appointment.getBoxCode() == null) {
            appointment.setBoxCode("BOX-" + String.format("%02d", (appointment.getServiceId() % 10) + 1));
        }

        return repository.save(appointment);
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(get(id));
    }
}
