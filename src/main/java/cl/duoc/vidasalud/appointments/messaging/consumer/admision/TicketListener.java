package cl.duoc.vidasalud.appointments.messaging.consumer.admision;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.duoc.vidasalud.appointments.domain.TicketService;
import cl.duoc.vidasalud.appointments.messaging.ErrorRecuperable;
import cl.duoc.vidasalud.appointments.messaging.events.AtencionEvent;

/**
 * Consumidor del dominio de admision: escucha la cola q.admision.ticket y
 * emite el ticket de la atencion confirmada.
 *
 * Los consumidores se agrupan por dominio funcional, un paquete por dominio
 * ({@code consumer.admision}), para que se vea de un vistazo a que parte del
 * sistema pertenece cada uno.
 *
 * El ACK es manual. El flujo de decision es siempre el mismo:
 *
 *   procesado bien            -> basicAck         (fuera de la cola)
 *   fallo transitorio, 1er    -> basicNack requeue (vuelve a intentarse)
 *   fallo transitorio, repite -> basicNack sin requeue (a la DLQ)
 *   mensaje invalido          -> basicNack sin requeue (a la DLQ)
 *
 * El limite de un reintento es deliberado: un mensaje que falla dos veces no
 * se arregla insistiendo, y dejarlo girando en la cola bloquea a los demas.
 * A la DLQ se va con log de nivel error, para que quede rastro de la perdida.
 */
@Component
public class TicketListener {

    private static final Logger log = LoggerFactory.getLogger(TicketListener.class);

    private final TicketService tickets;

    public TicketListener(TicketService tickets) {
        this.tickets = tickets;
    }

    @RabbitListener(queues = "${vidasalud.messaging.queues.ticket}")
    public void recibir(AtencionEvent evento,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                        @Header(name = AmqpHeaders.REDELIVERED, required = false) Boolean reentregado)
            throws IOException {

        boolean segundoIntento = Boolean.TRUE.equals(reentregado);

        // El patron de binding es atencion.#, asi que por esta cola pasan todos
        // los eventos de atencion. Los que no sean confirmaciones se descartan
        // con ACK: no son un error, simplemente no hay ticket que emitir.
        if (!"CONFIRMADA".equals(evento.status())) {
            log.debug("Evento {} de la atencion {} ignorado: no genera ticket.",
                    evento.status(), evento.atencionId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            String ticket = tickets.emitir(evento.atencionId());
            log.info("Mensaje procesado: ticket {} para la atencion {}", ticket, evento.atencionId());
            channel.basicAck(deliveryTag, false);

        } catch (IllegalArgumentException e) {
            // Mensaje definitivamente invalido: reintentarlo daria igual.
            log.error("Mensaje descartado a la DLQ. atencionId={} motivo={}",
                    evento.atencionId(), e.getMessage());
            channel.basicNack(deliveryTag, false, false);

        } catch (ErrorRecuperable e) {
            if (segundoIntento) {
                log.error("Fallo transitorio repetido; el mensaje de la atencion {} va a la DLQ.",
                        evento.atencionId(), e);
                channel.basicNack(deliveryTag, false, false);
            } else {
                log.warn("Fallo transitorio; se devuelve a la cola el mensaje de la atencion {}.",
                        evento.atencionId(), e);
                channel.basicNack(deliveryTag, false, true);
            }

        } catch (Exception e) {
            // Ante lo desconocido, un reintento y luego la DLQ. No se deja
            // girando: una cola bloqueada es peor que un mensaje en la DLQ.
            if (segundoIntento) {
                log.error("Error inesperado repetido; atencion {} a la DLQ.",
                        evento.atencionId(), e);
                channel.basicNack(deliveryTag, false, false);
            } else {
                log.error("Error inesperado procesando la atencion {}; se reintenta una vez.",
                        evento.atencionId(), e);
                channel.basicNack(deliveryTag, false, true);
            }
        }
    }

    /**
     * Consumidor de la DLQ de admision. No reprocesa: registra lo que se
     * perdio para poder auditarlo, y confirma para que la DLQ no crezca sin
     * control.
     */
    @RabbitListener(queues = "${vidasalud.messaging.dlq.ticket}")
    public void recibirDeLaDlq(AtencionEvent evento,
                               Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag)
            throws IOException {

        log.error("DLQ admision: la atencion {} del paciente {} quedo sin ticket.",
                evento.atencionId(), evento.patientEmail());
        channel.basicAck(deliveryTag, false);
    }
}
