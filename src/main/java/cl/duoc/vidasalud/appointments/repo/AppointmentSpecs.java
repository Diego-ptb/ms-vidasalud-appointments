package cl.duoc.vidasalud.appointments.repo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import cl.duoc.vidasalud.appointments.domain.Appointment;
import cl.duoc.vidasalud.appointments.domain.AppointmentStatus;

import jakarta.persistence.criteria.Predicate;

/** Filtros opcionales de la busqueda de atenciones. */
public final class AppointmentSpecs {

    private AppointmentSpecs() {
    }

    /**
     * Solo agrega al WHERE los filtros que vienen con valor. Asi el SQL nunca
     * lleva parametros nulos y PostgreSQL siempre puede inferir los tipos.
     */
    public static Specification<Appointment> search(AppointmentStatus status, Instant from, Instant to) {
        return search(null, status, from, to);
    }

    /**
     * Misma busqueda, acotada a un paciente. Se usa cuando quien llama es un
     * PACIENTE: nunca debe recibir atenciones de otras personas.
     */
    public static Specification<Appointment> search(String patientEmail, AppointmentStatus status,
                                                    Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();

            if (patientEmail != null && !patientEmail.isBlank()) {
                predicados.add(cb.equal(cb.lower(root.get("patientEmail")),
                        patientEmail.toLowerCase()));
            }

            if (status != null) {
                predicados.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicados.add(cb.greaterThanOrEqualTo(root.get("scheduledAt"), from));
            }
            if (to != null) {
                predicados.add(cb.lessThanOrEqualTo(root.get("scheduledAt"), to));
            }

            if (query != null) {
                query.orderBy(cb.asc(root.get("scheduledAt")));
            }

            return predicados.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicados.toArray(new Predicate[0]));
        };
    }
}
