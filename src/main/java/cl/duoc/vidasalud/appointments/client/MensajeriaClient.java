package cl.duoc.vidasalud.appointments.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import cl.duoc.vidasalud.appointments.domain.Appointment;
import cl.duoc.vidasalud.appointments.domain.AtencionEventos;

/**
 * Anuncia los hechos del dominio al microservicio de mensajeria.
 *
 * Este servicio NO habla AMQP: no conoce exchanges, colas ni routing keys, y
 * su pom no incluye el starter de RabbitMQ. Solo sabe que ocurrio un hecho y
 * a quien avisarle. Toda la mensajeria vive en ms-vidasalud-rabbit-admin.
 *
 * Dos decisiones que conviene conocer:
 *
 * 1. El aviso se manda DESPUES de que la transaccion hace commit. Si se
 *    mandara dentro, un consumidor rapido podria leer la base antes de que el
 *    cambio sea visible y procesar el estado viejo. Es el problema clasico de
 *    la escritura dual, y se manifiesta de forma intermitente: unas veces el
 *    mensaje gana la carrera y otras la pierde.
 *
 * 2. Un fallo al avisar NO puede voltear la operacion de negocio. Si el
 *    servicio de mensajeria esta caido, la atencion ya quedo confirmada en la
 *    base y eso vale; se registra el fallo y se sigue.
 */
@Component
public class MensajeriaClient implements AtencionEventos {

    private static final Logger log = LoggerFactory.getLogger(MensajeriaClient.class);

    private final RestClient restClient;
    private final String mensajeriaUrl;
    private final String internalKey;

    public MensajeriaClient(RestClient.Builder builder,
                            @Value("${vidasalud.mensajeria-url}") String mensajeriaUrl,
                            @Value("${vidasalud.internal-key}") String internalKey) {
        this.restClient = builder.build();
        this.mensajeriaUrl = mensajeriaUrl;
        this.internalKey = internalKey;
    }

    /** Lo que viaja al servicio de mensajeria. Es el contrato entre ambos. */
    public record EventoRequest(
            Long atencionId,
            String patientName,
            String patientEmail,
            Long serviceId,
            java.time.Instant scheduledAt,
            String status,
            String boxCode) {
    }

    @Override
    public void confirmada(Appointment atencion) {
        avisar("atencion-confirmada", atencion);
    }

    @Override
    public void cancelada(Appointment atencion) {
        avisar("atencion-cancelada", atencion);
    }

    private void avisar(String evento, Appointment atencion) {
        // El payload se arma ahora, con la entidad todavia en su contexto;
        // lo unico que se difiere es el envio.
        EventoRequest cuerpo = new EventoRequest(
                atencion.getId(),
                atencion.getPatientName(),
                atencion.getPatientEmail(),
                atencion.getServiceId(),
                atencion.getScheduledAt(),
                atencion.getStatus() == null ? null : atencion.getStatus().name(),
                atencion.getBoxCode());

        alConfirmarLaTransaccion(() -> enviar(evento, cuerpo));
    }

    private void enviar(String evento, EventoRequest cuerpo) {
        try {
            restClient.post()
                    .uri(mensajeriaUrl + "/api/mq/events/" + evento)
                    .header("X-Internal-Key", internalKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Hecho anunciado a la mensajeria: {} atencionId={}",
                    evento, cuerpo.atencionId());

        } catch (Exception e) {
            log.error("No se pudo anunciar {} de la atencion {} a la mensajeria. "
                    + "La operacion de negocio ya quedo guardada.",
                    evento, cuerpo.atencionId(), e);
        }
    }

    /**
     * Ejecuta el aviso cuando la transaccion en curso haga commit.
     *
     * Si no hay transaccion activa, avisa de inmediato: asi el cliente sigue
     * siendo usable desde una prueba o un flujo sin transaccion.
     *
     * Si la transaccion termina en rollback, el aviso no ocurre, que es lo
     * correcto: no se anuncia un hecho que no llego a pasar.
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
}
