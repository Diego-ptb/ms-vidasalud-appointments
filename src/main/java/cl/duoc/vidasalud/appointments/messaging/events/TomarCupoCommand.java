package cl.duoc.vidasalud.appointments.messaging.events;

/**
 * Orden de descontar un cupo del box de una prestacion.
 *
 * A diferencia de {@link AtencionEvent}, esto si es una orden con un unico
 * destinatario (ms-vidasalud-catalog), y por eso viaja por el exchange direct.
 */
public record TomarCupoCommand(
        Long serviceId,
        Long atencionId,
        String solicitadoPor) {
}
