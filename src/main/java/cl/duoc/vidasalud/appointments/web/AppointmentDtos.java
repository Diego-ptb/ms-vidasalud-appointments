package cl.duoc.vidasalud.appointments.web;

import java.time.Instant;

import cl.duoc.vidasalud.appointments.domain.Appointment;
import cl.duoc.vidasalud.appointments.domain.AppointmentStatus;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class AppointmentDtos {

    private AppointmentDtos() {
    }

    /**
     * No se acepta agendar en el pasado: una atencion con fecha anterior a
     * ahora no tiene sentido operativo.
     */
    public record CreateRequest(
            @NotBlank String patientName,
            @NotBlank @Email String patientEmail,
            @NotNull Long serviceId,
            @NotNull @Future Instant scheduledAt) {
    }

    public record StatusRequest(@NotNull AppointmentStatus status) {
    }

    public record Response(
            Long id,
            String patientName,
            String patientEmail,
            Long serviceId,
            String boxCode,
            Instant scheduledAt,
            AppointmentStatus status,
            String createdBy,
            Instant createdAt) {

        public static Response from(Appointment a) {
            return new Response(a.getId(), a.getPatientName(), a.getPatientEmail(),
                    a.getServiceId(), a.getBoxCode(), a.getScheduledAt(), a.getStatus(),
                    a.getCreatedBy(), a.getCreatedAt());
        }
    }
}
