package cl.duoc.vidasalud.appointments.messaging;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
 * Dos decisiones importantes:
 *
 * 1. El mensaje se envia DESPUES de que la transaccion hace commit. Si se
 *    enviara dentro, un consumidor rapido podria leer la base antes de que el
 *    cambio sea visible y procesar datos viejos. Es el problema clasico de la
 *    escritura dual, y se manifiesta de forma intermitente: el mensaje gana a
 *    veces y pierde otras.
 *
 * 2. Publicar NO puede voltear la operacion de negocio. Si el broker esta
 *    caido, la atencion ya quedo confirmada en la base y eso vale; se registra
 *    el fallo y se sigue. Es justamente lo que se gana desacoplando: la
 *    mensajeria no afecta la logica existente del sistema.
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
        // Los payloads se arman ahora, con la entidad todavia en su contexto;
        // lo unico que se difiere es el envio.
        AtencionEvent evento = eventoDe(atencion);
        TomarCupoCommand comando = new TomarCupoCommand(
                atencion.getServiceId(), atencion.getId(), atencion.getCreatedBy());
        String routingKey = nombres.getRouting().getAtencionConfirmada();

        alConfirmarLaTransaccion(() -> {
            publicarEvento(routingKey, evento);
            ordenarDescuentoDeCupo(comando);
        });
    }

    @Override
    public void cancelada(Appointment atencion) {
        AtencionEvent evento = eventoDe(atencion);
        String routingKey = nombres.getRouting().getAtencionCancelada();

        alConfirmarLaTransaccion(() -> publicarEvento(routingKey, evento));
    }

    /**
     * Ejecuta el envio cuando la transaccion en curso haga commit.
     *
     * Si no hay transaccion activa, envia de inmediato: asi el productor sigue
     * siendo usable desde una prueba o desde un flujo sin transaccion, sin
     * tener que saber en cual de los dos casos esta.
     *
     * Si la transaccion termina en rollback, el envio sencillamente no ocurre,
     * que es lo correcto: no se anuncia un hecho que no llego a pasar.
     */
    private void alConfirmarLaTransaccion(Runnable envio) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            envio.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                envio.run();
            }
        });
    }

    private AtencionEvent eventoDe(Appointment atencion) {
        return new AtencionEvent(
                atencion.getId(),
                atencion.getPatientName(),
                atencion.getPatientEmail(),
                atencion.getServiceId(),
                atencion.getScheduledAt(),
                atencion.getStatus() == null ? null : atencion.getStatus().name(),
                atencion.getBoxCode(),
                Instant.now());
    }

    private void publicarEvento(String routingKey, AtencionEvent evento) {
        try {
            rabbit.convertAndSend(nombres.getExchanges().getTopic(), routingKey, evento);
            log.info("Evento publicado. exchange={} routingKey={} atencionId={}",
                    nombres.getExchanges().getTopic(), routingKey, evento.atencionId());
        } catch (AmqpException e) {
            log.error("No se pudo publicar el evento {} de la atencion {}. "
                    + "La operacion de negocio ya quedo guardada.",
                    routingKey, evento.atencionId(), e);
        }
    }

    private void ordenarDescuentoDeCupo(TomarCupoCommand comando) {
        try {
            rabbit.convertAndSend(nombres.getExchanges().getDirect(),
                    nombres.getRouting().getCupoTomar(), comando);
            log.info("Comando de descuento de cupo enviado. serviceId={} atencionId={}",
                    comando.serviceId(), comando.atencionId());
        } catch (AmqpException e) {
            log.error("No se pudo enviar el descuento de cupo de la atencion {}",
                    comando.atencionId(), e);
        }
    }
}
