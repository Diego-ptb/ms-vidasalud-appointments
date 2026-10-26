package cl.duoc.vidasalud.appointments.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import cl.duoc.vidasalud.appointments.domain.Appointment;
import cl.duoc.vidasalud.appointments.domain.AppointmentStatus;
import cl.duoc.vidasalud.appointments.messaging.events.AtencionEvent;
import cl.duoc.vidasalud.appointments.messaging.events.TomarCupoCommand;

/**
 * El punto delicado del productor no es serializar: es CUANDO envia.
 *
 * Publicar dentro de la transaccion deja que un consumidor rapido lea la base
 * antes del commit y procese datos viejos. El fallo es intermitente, asi que
 * conviene fijarlo con una prueba en vez de confiar en que no vuelva.
 */
@ExtendWith(MockitoExtension.class)
class AtencionEventPublisherTest {

    @Mock
    private RabbitTemplate rabbit;

    private MessagingProperties nombres;
    private AtencionEventPublisher publisher;

    @BeforeEach
    void setUp() {
        nombres = new MessagingProperties();
        publisher = new AtencionEventPublisher(rabbit, nombres);
    }

    @AfterEach
    void limpiarTransaccion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Appointment atencionConfirmada() {
        Appointment a = new Appointment();
        a.setId(6L);
        a.setPatientName("Ana Perez");
        a.setPatientEmail("ana@example.cl");
        a.setServiceId(1L);
        a.setScheduledAt(Instant.now().plus(1, ChronoUnit.DAYS));
        a.setStatus(AppointmentStatus.CONFIRMADA);
        a.setBoxCode("BOX-02");
        a.setCreatedBy("recepcion@vidasalud.cl");
        return a;
    }

    // ------------------------------------------------------- sin transaccion

    @Test
    @DisplayName("Sin transaccion activa publica de inmediato")
    void publicaDeInmediatoSinTransaccion() {
        publisher.confirmada(atencionConfirmada());

        // Dos mensajes: el evento al topic y el comando al direct.
        verify(rabbit).convertAndSend(eq("vidasalud.atenciones.topic"),
                eq("atencion.confirmada"), any(AtencionEvent.class));
        verify(rabbit).convertAndSend(eq("vidasalud.comandos.direct"),
                eq("cupo.tomar"), any(TomarCupoCommand.class));
    }

    @Test
    @DisplayName("El evento lleva los datos de la atencion")
    void elEventoLlevaLosDatos() {
        publisher.confirmada(atencionConfirmada());

        ArgumentCaptor<AtencionEvent> captor = ArgumentCaptor.forClass(AtencionEvent.class);
        verify(rabbit).convertAndSend(eq("vidasalud.atenciones.topic"),
                eq("atencion.confirmada"), captor.capture());

        AtencionEvent evento = captor.getValue();
        assertThat(evento.atencionId()).isEqualTo(6L);
        assertThat(evento.patientEmail()).isEqualTo("ana@example.cl");
        assertThat(evento.status()).isEqualTo("CONFIRMADA");
        assertThat(evento.boxCode()).isEqualTo("BOX-02");
    }

    @Test
    @DisplayName("Cancelar publica solo el evento, no el descuento de cupo")
    void cancelarNoDescuentaCupo() {
        publisher.cancelada(atencionConfirmada());

        verify(rabbit).convertAndSend(eq("vidasalud.atenciones.topic"),
                eq("atencion.cancelada"), any(AtencionEvent.class));
        verify(rabbit, never()).convertAndSend(eq("vidasalud.comandos.direct"),
                anyString(), any(TomarCupoCommand.class));
    }

    // ------------------------------------------------------- con transaccion

    @Test
    @DisplayName("Con transaccion activa NO envia hasta el commit")
    void esperaAlCommit() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.confirmada(atencionConfirmada());

        // Nada todavia: el cambio aun no es visible para los consumidores.
        verify(rabbit, never()).convertAndSend(anyString(), anyString(), any(AtencionEvent.class));
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }

    @Test
    @DisplayName("Al hacer commit se envian los dos mensajes")
    void enviaDespuesDelCommit() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.confirmada(atencionConfirmada());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sincronizacion -> sincronizacion.afterCommit());

        verify(rabbit).convertAndSend(eq("vidasalud.atenciones.topic"),
                eq("atencion.confirmada"), any(AtencionEvent.class));
        verify(rabbit).convertAndSend(eq("vidasalud.comandos.direct"),
                eq("cupo.tomar"), any(TomarCupoCommand.class));
    }

    @Test
    @DisplayName("Si la transaccion no hace commit, no se anuncia nada")
    void noAnunciaSiHayRollback() {
        // No se anuncia un hecho que no llego a ocurrir.
        TransactionSynchronizationManager.initSynchronization();

        publisher.confirmada(atencionConfirmada());
        TransactionSynchronizationManager.clearSynchronization();

        verify(rabbit, never()).convertAndSend(anyString(), anyString(), any(AtencionEvent.class));
    }

    // ------------------------------------------------------------- resiliencia

    @Test
    @DisplayName("Un fallo del broker no propaga la excepcion")
    void elFalloDelBrokerNoRompeElNegocio() {
        // La atencion ya quedo guardada: que el broker este caido no puede
        // voltear la operacion de negocio.
        doThrow(new AmqpException("broker caido"))
                .when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class));

        publisher.confirmada(atencionConfirmada());
    }
}
