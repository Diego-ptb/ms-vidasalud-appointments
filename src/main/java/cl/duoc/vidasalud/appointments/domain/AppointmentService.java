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
    private final AtencionEventos eventos;

    public AppointmentService(AppointmentRepository repository, AtencionEventos eventos) {
        this.repository = repository;
        this.eventos = eventos;
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
     *
     * Al confirmar o cancelar se anuncia el hecho por {@link AtencionEventos};
     * el trabajo asincrono lo recogen los consumidores de las colas.
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

        // Al confirmar se asigna el box.
        if (target == AppointmentStatus.CONFIRMADA && appointment.getBoxCode() == null) {
            appointment.setBoxCode("BOX-" + String.format("%02d", (appointment.getServiceId() % 10) + 1));
        }

        Appointment guardada = repository.save(appointment);

        // El anuncio va despues de guardar: solo se publica lo que ya es un
        // hecho. Las tareas lentas (ticket, correo, descuento de cupo) las
        // hacen los consumidores, no esta llamada HTTP.
        if (target == AppointmentStatus.CONFIRMADA) {
            eventos.confirmada(guardada);
        } else if (target == AppointmentStatus.CANCELADA) {
            eventos.cancelada(guardada);
        }

        return guardada;
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(get(id));
    }
}
