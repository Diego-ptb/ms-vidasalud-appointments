package cl.duoc.vidasalud.appointments.web;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Identidad de quien llama, tal como la propaga el BFF desde el token ya
 * validado (cabeceras X-User, X-User-Email y X-Roles).
 *
 * Se usa para acotar lo que cada rol puede ver: un paciente solo accede a sus
 * propias atenciones, no a las de la red completa.
 */
public record CallerContext(String subject, String email, List<String> roles) {

    public static CallerContext de(String subject, String email, String rolesCsv) {
        List<String> roles = (rolesCsv == null || rolesCsv.isBlank())
                ? List.of()
                : Arrays.stream(rolesCsv.split(","))
                        .map(String::trim)
                        .map(r -> r.toUpperCase(Locale.ROOT))
                        .toList();

        return new CallerContext(subject == null ? "" : subject,
                email == null ? "" : email,
                roles);
    }

    public boolean tieneRol(String rol) {
        return roles.contains(rol);
    }

    /** Admin y recepcionista operan sobre toda la red. */
    public boolean esPersonal() {
        return tieneRol("ADMIN") || tieneRol("RECEPCIONISTA");
    }

    /** Un paciente sin rol de personal solo puede ver lo suyo. */
    public boolean soloVeLoPropio() {
        return !esPersonal();
    }

    public boolean esDuenoDe(String patientEmail) {
        return email != null && !email.isBlank() && email.equalsIgnoreCase(patientEmail);
    }
}
