package cl.duoc.vidasalud.appointments.domain;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.duoc.vidasalud.appointments.repo.AppointmentRepository;

/**
 * Generacion del ticket de admision, la tarea asincrona del dominio.
 *
 * Es logica de negocio pura: no conoce RabbitMQ ni cabeceras AMQP. Quien la
 * dispara (un consumidor de cola hoy, un endpoint manana) le da igual.
 */
@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final AppointmentRepository repository;

    public TicketService(AppointmentRepository repository) {
        this.repository = repository;
    }

    /**
     * Emite el ticket de una atencion y lo guarda.
     *
     * Es idempotente: si la atencion ya tiene ticket, no se genera otro. Hace
     * falta porque RabbitMQ garantiza "al menos una entrega", no "exactamente
     * una": un mensaje reintentado puede llegar dos veces.
     *
     * @return el codigo del ticket
     * @throws IllegalArgumentException si la atencion no existe o no esta en
     *         un estado que admita ticket; son errores definitivos, no
     *         reintentables, y el consumidor los manda a la DLQ.
     */
    @Transactional
    public String emitir(Long atencionId) {
        Appointment atencion = repository.findById(atencionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la atencion " + atencionId + ": no se puede emitir el ticket."));

        if (atencion.getTicketCode() != null) {
            log.info("La atencion {} ya tenia el ticket {}; no se emite otro.",
                    atencionId, atencion.getTicketCode());
            return atencion.getTicketCode();
        }

        if (atencion.getStatus() != AppointmentStatus.CONFIRMADA) {
            throw new IllegalArgumentException("La atencion " + atencionId + " esta en estado "
                    + atencion.getStatus() + " y solo se emite ticket de una CONFIRMADA.");
        }

        String codigo = codigoPara(atencion);
        atencion.setTicketCode(codigo);
        repository.save(atencion);

        log.info("Ticket {} emitido para la atencion {} (box {})",
                codigo, atencionId, atencion.getBoxCode());
        return codigo;
    }

    /** Codigo legible para el paciente: box + numero de atencion. */
    private String codigoPara(Appointment atencion) {
        String box = atencion.getBoxCode() == null ? "GEN" : atencion.getBoxCode();
        return (box + "-" + String.format("%05d", atencion.getId())).toUpperCase(Locale.ROOT);
    }
}
