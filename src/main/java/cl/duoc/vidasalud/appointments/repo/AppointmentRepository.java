package cl.duoc.vidasalud.appointments.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import cl.duoc.vidasalud.appointments.domain.Appointment;
import cl.duoc.vidasalud.appointments.domain.AppointmentStatus;

/**
 * Los filtros opcionales (estado, desde, hasta) se arman con Specifications en
 * AppointmentSpecs: un JPQL con "(:param is null or ...)" falla en PostgreSQL
 * con "could not determine data type of parameter", porque no puede inferir el
 * tipo de un parametro nulo.
 */
public interface AppointmentRepository
        extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    List<Appointment> findByStatusOrderByScheduledAtAsc(AppointmentStatus status);

    List<Appointment> findByPatientEmailOrderByScheduledAtDesc(String patientEmail);
}
