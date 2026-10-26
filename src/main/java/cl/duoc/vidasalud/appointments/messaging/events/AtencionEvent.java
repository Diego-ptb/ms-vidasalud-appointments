package cl.duoc.vidasalud.appointments.messaging.events;

import java.time.Instant;

/**
 * Lo que le ocurrio a una atencion. Es un hecho ya consumado, no una orden:
 * quien lo reciba decide que hacer con el.
 *
 * Viaja como JSON, asi que el contrato son los nombres de los campos. Los
 * servicios que lo consumen tienen su propia copia de este record: cada
 * repositorio es independiente y no comparten un jar comun.
 */
public record AtencionEvent(
        Long atencionId,
        String patientName,
        String patientEmail,
        Long serviceId,
        Instant scheduledAt,
        String status,
        String boxCode,
        Instant ocurridoEn) {
}
