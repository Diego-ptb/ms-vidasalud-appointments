package cl.duoc.vidasalud.appointments.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import org.mockito.ArgumentMatchers;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.data.jpa.domain.Specification;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import cl.duoc.vidasalud.appointments.repo.AppointmentRepository;
import cl.duoc.vidasalud.appointments.web.AppointmentDtos.CreateRequest;

/**
 * Reglas de negocio del servicio de atenciones, con el repositorio simulado:
 * las pruebas no dependen de que haya una base de datos disponible.
 */
@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository repository;

    private AppointmentService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(repository);
    }

    private CreateRequest solicitudPara(Instant fecha) {
        return new CreateRequest("Ana Perez", "ana@example.cl", 1L, fecha);
    }

    private Appointment atencionEn(AppointmentStatus estado) {
        Appointment atencion = new Appointment();
        atencion.setId(1L);
        atencion.setPatientName("Ana Perez");
        atencion.setPatientEmail("ana@example.cl");
        atencion.setServiceId(1L);
        atencion.setScheduledAt(Instant.now().plus(1, ChronoUnit.DAYS));
        atencion.setStatus(estado);
        return atencion;
    }

    @Test
    @DisplayName("Una atencion nueva nace en estado SOLICITADA")
    void creaLaAtencionEnEstadoInicial() {
        when(repository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        Appointment creada = service.create(
                solicitudPara(Instant.now().plus(2, ChronoUnit.DAYS)), "recepcion@vidasalud.cl");

        assertThat(creada.getStatus()).isEqualTo(AppointmentStatus.SOLICITADA);
        assertThat(creada.getCreatedBy()).isEqualTo("recepcion@vidasalud.cl");
    }

    @Test
    @DisplayName("No se puede agendar una atencion en el pasado")
    void rechazaFechasPasadas() {
        Instant ayer = Instant.now().minus(1, ChronoUnit.DAYS);

        assertThatThrownBy(() -> service.create(solicitudPara(ayer), "recepcion@vidasalud.cl"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("futura");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Confirmar una atencion le asigna un box")
    void alConfirmarAsignaBox() {
        when(repository.findById(1L)).thenReturn(Optional.of(atencionEn(AppointmentStatus.SOLICITADA)));
        when(repository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));

        Appointment confirmada = service.changeStatus(1L, AppointmentStatus.CONFIRMADA);

        assertThat(confirmada.getStatus()).isEqualTo(AppointmentStatus.CONFIRMADA);
        assertThat(confirmada.getBoxCode()).isNotNull();
        assertThat(confirmada.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Una transicion invalida responde 409 y no se guarda")
    void rechazaTransicionInvalida() {
        when(repository.findById(1L)).thenReturn(Optional.of(atencionEn(AppointmentStatus.SOLICITADA)));

        assertThatThrownBy(() -> service.changeStatus(1L, AppointmentStatus.EN_ATENCION))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Buscar una atencion inexistente responde 404")
    void respondeNotFoundSiNoExiste() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Un paciente sin identidad no recibe ninguna atencion")
    void sinIdentidadNoDevuelveNada() {
        // Preferimos no devolver nada antes que arriesgarnos a devolver todo.
        assertThat(service.searchDelPaciente(null, null, null, null)).isEmpty();
        assertThat(service.searchDelPaciente("  ", null, null, null)).isEmpty();

        verify(repository, never()).findAll(ArgumentMatchers.<Specification<Appointment>>any());
    }

    @Test
    @DisplayName("La busqueda de un paciente consulta el repositorio con un filtro")
    void acotaLaBusquedaAlPaciente() {
        when(repository.findAll(ArgumentMatchers.<Specification<Appointment>>any()))
                .thenReturn(List.of(atencionEn(AppointmentStatus.SOLICITADA)));

        List<Appointment> resultado = service.searchDelPaciente("ana@example.cl", null, null, null);

        assertThat(resultado).hasSize(1);
        verify(repository).findAll(ArgumentMatchers.<Specification<Appointment>>any());
    }
}
