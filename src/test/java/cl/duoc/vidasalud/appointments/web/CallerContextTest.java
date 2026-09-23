package cl.duoc.vidasalud.appointments.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * De este objeto depende que un paciente no vea las atenciones de otros, asi
 * que conviene probarlo por separado: es una decision de seguridad, no un
 * detalle de formato.
 */
class CallerContextTest {

    @Test
    @DisplayName("Admin y recepcionista operan sobre toda la red")
    void elPersonalVeTodo() {
        assertThat(CallerContext.de("sub", "admin@vidasalud.cl", "ADMIN").soloVeLoPropio()).isFalse();
        assertThat(CallerContext.de("sub", "rec@vidasalud.cl", "RECEPCIONISTA").soloVeLoPropio()).isFalse();
        assertThat(CallerContext.de("sub", "x@vidasalud.cl", "ADMIN,AUDITOR").esPersonal()).isTrue();
    }

    @Test
    @DisplayName("Un paciente solo accede a lo suyo")
    void elPacienteSoloVeLoPropio() {
        CallerContext paciente = CallerContext.de("sub", "ana@example.cl", "PACIENTE");

        assertThat(paciente.soloVeLoPropio()).isTrue();
        assertThat(paciente.esDuenoDe("ana@example.cl")).isTrue();
        assertThat(paciente.esDuenoDe("otro@example.cl")).isFalse();
    }

    @Test
    @DisplayName("El correo se compara sin distinguir mayusculas")
    void comparaElCorreoSinDistinguirMayusculas() {
        // Entra ID puede devolver el correo con otra capitalizacion que la
        // guardada en la base; no debe cambiar quien es el dueno.
        CallerContext paciente = CallerContext.de("sub", "Ana@Example.CL", "PACIENTE");

        assertThat(paciente.esDuenoDe("ana@example.cl")).isTrue();
    }

    @Test
    @DisplayName("Sin roles se trata como paciente, no como personal")
    void sinRolesNoEsPersonal() {
        // Ante la duda, el menor privilegio: quien no trae roles no administra.
        CallerContext sinRoles = CallerContext.de("sub", "x@example.cl", null);

        assertThat(sinRoles.esPersonal()).isFalse();
        assertThat(sinRoles.soloVeLoPropio()).isTrue();
        assertThat(sinRoles.roles()).isEmpty();
    }

    @Test
    @DisplayName("Sin correo no es dueno de nada")
    void sinCorreoNoEsDuenoDeNada() {
        assertThat(CallerContext.de("sub", null, "PACIENTE").esDuenoDe("ana@example.cl")).isFalse();
        assertThat(CallerContext.de("sub", "", "PACIENTE").esDuenoDe("")).isFalse();
    }

    @Test
    @DisplayName("Los roles se normalizan a mayusculas y sin espacios")
    void normalizaLosRoles() {
        CallerContext caller = CallerContext.de("sub", "x@example.cl", " admin , recepcionista ");

        assertThat(caller.roles()).containsExactly("ADMIN", "RECEPCIONISTA");
        assertThat(caller.tieneRol("ADMIN")).isTrue();
    }
}
