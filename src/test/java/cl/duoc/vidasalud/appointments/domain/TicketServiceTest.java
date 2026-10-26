package cl.duoc.vidasalud.appointments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cl.duoc.vidasalud.appointments.repo.AppointmentRepository;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private AppointmentRepository repository;

    private TicketService service;

    @BeforeEach
    void setUp() {
        service = new TicketService(repository);
    }

    private Appointment atencion(AppointmentStatus estado, String ticket) {
        Appointment a = new Appointment();
        a.setId(7L);
        a.setPatientName("Ana Perez");
        a.setPatientEmail("ana@example.cl");
        a.setServiceId(1L);
        a.setScheduledAt(Instant.now().plus(1, ChronoUnit.DAYS));
        a.setStatus(estado);
        a.setBoxCode("BOX-01");
        a.setTicketCode(ticket);
        return a;
    }

    @Test
    @DisplayName("Emite el ticket con el box y el numero de atencion")
    void emiteElTicket() {
        when(repository.findById(7L)).thenReturn(Optional.of(atencion(AppointmentStatus.CONFIRMADA, null)));
        when(repository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.emitir(7L)).isEqualTo("BOX-01-00007");
    }

    @Test
    @DisplayName("Si el mensaje llega dos veces no se emite un segundo ticket")
    void esIdempotente() {
        // RabbitMQ garantiza "al menos una entrega": el mensaje puede repetirse.
        when(repository.findById(7L))
                .thenReturn(Optional.of(atencion(AppointmentStatus.CONFIRMADA, "BOX-01-00007")));

        assertThat(service.emitir(7L)).isEqualTo("BOX-01-00007");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Una atencion que no existe es un error definitivo")
    void fallaSiLaAtencionNoExiste() {
        when(repository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.emitir(7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No existe la atencion 7");
    }

    @Test
    @DisplayName("Solo se emite ticket de una atencion CONFIRMADA")
    void noEmiteSiNoEstaConfirmada() {
        when(repository.findById(7L)).thenReturn(Optional.of(atencion(AppointmentStatus.SOLICITADA, null)));

        assertThatThrownBy(() -> service.emitir(7L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SOLICITADA");

        verify(repository, never()).save(any());
    }
}
