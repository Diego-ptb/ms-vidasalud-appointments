package cl.duoc.vidasalud.appointments.messaging.consumer.admision;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rabbitmq.client.Channel;

import cl.duoc.vidasalud.appointments.domain.TicketService;
import cl.duoc.vidasalud.appointments.messaging.ErrorRecuperable;
import cl.duoc.vidasalud.appointments.messaging.events.AtencionEvent;

/**
 * Lo que se prueba aqui no es RabbitMQ, es la decision del consumidor: cuando
 * confirma, cuando reintenta y cuando manda el mensaje a la DLQ. Es la parte
 * que se puede equivocar y perder mensajes, asi que va cubierta.
 *
 * El Channel se simula, por lo que no hace falta broker para ejecutarlas.
 */
@ExtendWith(MockitoExtension.class)
class TicketListenerTest {

    private static final long TAG = 42L;

    @Mock
    private TicketService tickets;

    @Mock
    private Channel channel;

    private TicketListener listener;

    @BeforeEach
    void setUp() {
        listener = new TicketListener(tickets);
    }

    private AtencionEvent eventoCon(String estado) {
        return new AtencionEvent(7L, "Ana Perez", "ana@example.cl", 1L,
                Instant.now(), estado, "BOX-01", Instant.now());
    }

    @Test
    @DisplayName("Procesado con exito: ACK y el mensaje sale de la cola")
    void confirmaCuandoProcesaBien() throws IOException {
        when(tickets.emitir(7L)).thenReturn("BOX-01-00007");

        listener.recibir(eventoCon("CONFIRMADA"), channel, TAG, false);

        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("Un evento que no es confirmacion se descarta con ACK, no es error")
    void ignoraLosEventosQueNoGeneranTicket() throws IOException {
        // La cola esta ligada a atencion.#, asi que tambien recibe cancelaciones.
        listener.recibir(eventoCon("CANCELADA"), channel, TAG, false);

        verify(channel).basicAck(TAG, false);
        verify(tickets, never()).emitir(anyLong());
    }

    @Test
    @DisplayName("Mensaje invalido: NACK sin requeue, directo a la DLQ")
    void mandaALaDlqLoQueNoTieneArreglo() throws IOException {
        when(tickets.emitir(7L)).thenThrow(new IllegalArgumentException("No existe la atencion 7"));

        listener.recibir(eventoCon("CONFIRMADA"), channel, TAG, false);

        // requeue=false: insistir con un mensaje invalido solo tapa la cola.
        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    @DisplayName("Fallo transitorio en el primer intento: NACK con requeue")
    void reintentaUnaVezAnteUnFalloTransitorio() throws IOException {
        when(tickets.emitir(7L)).thenThrow(new ErrorRecuperable("base no responde", null));

        listener.recibir(eventoCon("CONFIRMADA"), channel, TAG, false);

        verify(channel).basicNack(TAG, false, true);
    }

    @Test
    @DisplayName("Fallo transitorio que se repite: a la DLQ, no gira para siempre")
    void dejaDeReintentarYVaALaDlq() throws IOException {
        when(tickets.emitir(7L)).thenThrow(new ErrorRecuperable("base no responde", null));

        // reentregado = true: ya se intento una vez.
        listener.recibir(eventoCon("CONFIRMADA"), channel, TAG, true);

        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    @DisplayName("La DLQ se consume y se confirma, para que no crezca sin control")
    void consumeLaDlq() throws IOException {
        listener.recibirDeLaDlq(eventoCon("CONFIRMADA"), channel, TAG);

        verify(channel).basicAck(TAG, false);
    }
}
