package cl.duoc.vidasalud.appointments.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * La maquina de estados es la regla central del caso VidaSalud, asi que se
 * prueba de forma aislada: sin base de datos ni contexto de Spring.
 */
class AppointmentStatusTest {

    @ParameterizedTest(name = "{0} -> {1} es valida")
    @CsvSource({
            "SOLICITADA,  CONFIRMADA",
            "SOLICITADA,  CANCELADA",
            "CONFIRMADA,  EN_ESPERA",
            "CONFIRMADA,  CANCELADA",
            "EN_ESPERA,   EN_ATENCION",
            "EN_ESPERA,   CANCELADA",
            "EN_ATENCION, CERRADA"
    })
    void permiteLasTransicionesDelFlujo(AppointmentStatus desde, AppointmentStatus hasta) {
        assertThat(desde.canMoveTo(hasta)).isTrue();
    }

    @Test
    @DisplayName("No se puede pasar a EN_ATENCION sin haber confirmado")
    void noPermiteAtenderSinConfirmar() {
        // Regla explicita del caso: "No se puede pasar a EN_ATENCION sin CONFIRMAR".
        assertThat(AppointmentStatus.SOLICITADA.canMoveTo(AppointmentStatus.EN_ATENCION)).isFalse();
        assertThat(AppointmentStatus.CONFIRMADA.canMoveTo(AppointmentStatus.EN_ATENCION)).isFalse();
    }

    @ParameterizedTest(name = "{0} es un estado final")
    @EnumSource(value = AppointmentStatus.class, names = { "CERRADA", "CANCELADA" })
    void losEstadosFinalesNoTienenSalida(AppointmentStatus estadoFinal) {
        assertThat(estadoFinal.allowedNext()).isEmpty();

        for (AppointmentStatus destino : AppointmentStatus.values()) {
            assertThat(estadoFinal.canMoveTo(destino)).isFalse();
        }
    }

    @Test
    @DisplayName("No se puede saltar pasos ni retroceder")
    void noPermiteSaltosNiRetrocesos() {
        assertThat(AppointmentStatus.SOLICITADA.canMoveTo(AppointmentStatus.CERRADA)).isFalse();
        assertThat(AppointmentStatus.CONFIRMADA.canMoveTo(AppointmentStatus.CERRADA)).isFalse();
        assertThat(AppointmentStatus.EN_ATENCION.canMoveTo(AppointmentStatus.SOLICITADA)).isFalse();
        assertThat(AppointmentStatus.EN_ESPERA.canMoveTo(AppointmentStatus.CONFIRMADA)).isFalse();
    }

    @Test
    @DisplayName("Una atencion en curso no puede cancelarse")
    void noPermiteCancelarUnaAtencionEnCurso() {
        // Si el paciente ya esta siendo atendido, el cierre es el unico camino.
        assertThat(AppointmentStatus.EN_ATENCION.canMoveTo(AppointmentStatus.CANCELADA)).isFalse();
    }

    @Test
    void ningunEstadoPuedeTransicionarASiMismo() {
        for (AppointmentStatus estado : AppointmentStatus.values()) {
            assertThat(estado.canMoveTo(estado)).isFalse();
        }
    }
}
