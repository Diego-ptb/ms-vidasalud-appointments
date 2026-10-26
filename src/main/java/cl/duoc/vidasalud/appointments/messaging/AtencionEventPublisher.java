package cl.duoc.vidasalud.appointments.messaging;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import cl.duoc.vidasalud.appointments.domain.Appointment;
import cl.duoc.vidasalud.appointments.domain.AtencionEventos;
import cl.duoc.vidasalud.appointments.messaging.events.AtencionEvent;
import cl.duoc.vidasalud.appointments.messaging.events.TomarCupoCommand;

/**
 * Productor del dominio de atenciones.
 *
 * Es la unica puerta de salida hacia RabbitMQ: el servicio de dominio llama a
 * metodos con nombre de negocio ("confirmada", "cancelada") y no sabe que hay
 * un exchange detras.
 *
 * Decision importante: publicar NO puede voltear la operacion de negocio. Si
 * el broker esta caido, la atencion ya quedo confirmada en la base y eso vale;
 * se registra el fallo y se sigue. Es justamente lo que se gana desacoplando:
 * la mensajeria no afecta la logica existente del sistema.
 */
@Component
public class AtencionEventPublisher implements AtencionEventos {

    private static final Logger log = LoggerFactory.getLogger(AtencionEventPublisher.class);

    private final RabbitTemplate rabbit;
    private final MessagingProperties nombres;

    public AtencionEventPublisher(RabbitTemplate rabbit, MessagingProperties nombres) {
        this.rabbit = rabbit;
        this.nombres = nombres;
    }

    /**
     * Publica el evento de atencion confirmada y manda el comando de descuento
     * de cupo. Son dos mensajes distintos a proposito: uno es un hecho para
     * quien quiera enterarse, el otro una orden para el catalogo.
     */
    @Override
    public void confirmada(Appointment atencion) {
        publicarEvento(nombres.getRouting().getAtencionConfirmada(), atencion);
        ordenarDescuentoDeCupo(atencion);
    }

    @Override
    public void cancelada(Appointment atencion) {
        publicarEvento(nombres.getRouting().getAtencionCancelada(), atencion);
    }

    private void publicarEvento(String routingKey, Appointment atencion) {
        AtencionEvent evento = new AtencionEvent(
                atencion.getId(),
                atencion.getPatientName(),
                atencion.getPatientEmail(),
                atencion.getServiceId(),
                atencion.getScheduledAt(),
                atencion.getStatus() == null ? null : atencion.getStatus().name(),
                atencion.getBoxCode(),
                Instant.now());

        try {
            rabbit.convertAndSend(nombres.getExchanges().getTopic(), routingKey, evento);
            log.info("Evento publicado. exchange={} routingKey={} atencionId={}",
                    nombres.getExchanges().getTopic(), routingKey, atencion.getId());
        } catch (AmqpException e) {
            log.error("No se pudo publicar el evento {} de la atencion {}. "
                    + "La operacion de negocio ya quedo guardada.",
                    routingKey, atencion.getId(), e);
        }
    }

    private void ordenarDescuentoDeCupo(Appointment atencion) {
        TomarCupoCommand comando = new TomarCupoCommand(
                atencion.getServiceId(), atencion.getId(), atencion.getCreatedBy());

        try {
            rabbit.convertAndSend(nombres.getExchanges().getDirect(),
                    nombres.getRouting().getCupoTomar(), comando);
            log.info("Comando de descuento de cupo enviado. serviceId={} atencionId={}",
                    atencion.getServiceId(), atencion.getId());
        } catch (AmqpException e) {
            log.error("No se pudo enviar el descuento de cupo de la atencion {}",
                    atencion.getId(), e);
        }
    }
}
