package cl.duoc.vidasalud.appointments.domain;

import java.util.List;
import java.util.Map;

/**
 * Maquina de estados de la atencion. Regla clave del caso:
 * no se puede pasar a EN_ATENCION sin haber CONFIRMADO antes.
 */
public enum AppointmentStatus {

    SOLICITADA,
    CONFIRMADA,
    EN_ESPERA,
    EN_ATENCION,
    CERRADA,
    CANCELADA;

    private static final Map<AppointmentStatus, List<AppointmentStatus>> TRANSITIONS = Map.of(
            SOLICITADA, List.of(CONFIRMADA, CANCELADA),
            CONFIRMADA, List.of(EN_ESPERA, CANCELADA),
            EN_ESPERA, List.of(EN_ATENCION, CANCELADA),
            EN_ATENCION, List.of(CERRADA),
            CERRADA, List.of(),
            CANCELADA, List.of());

    public boolean canMoveTo(AppointmentStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public List<AppointmentStatus> allowedNext() {
        return TRANSITIONS.get(this);
    }
}
