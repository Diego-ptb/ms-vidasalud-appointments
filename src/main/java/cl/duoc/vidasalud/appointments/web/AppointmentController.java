package cl.duoc.vidasalud.appointments.web;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import cl.duoc.vidasalud.appointments.domain.Appointment;
import cl.duoc.vidasalud.appointments.domain.AppointmentService;
import cl.duoc.vidasalud.appointments.domain.AppointmentStatus;
import cl.duoc.vidasalud.appointments.web.AppointmentDtos.CreateRequest;
import cl.duoc.vidasalud.appointments.web.AppointmentDtos.Response;
import cl.duoc.vidasalud.appointments.web.AppointmentDtos.StatusRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    /**
     * Admin y recepcionista ven toda la red; el paciente, solo sus atenciones.
     *
     * El filtro se aplica aqui y no en el frontend: la lista viaja por la red y
     * cualquiera podria pedirla con curl. Los datos de un paciente no pueden
     * salir del servicio hacia alguien que no tiene por que verlos.
     */
    @GetMapping
    public List<Response> list(
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestHeader(value = "X-User", required = false) String user,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @RequestHeader(value = "X-Roles", required = false) String roles) {

        CallerContext caller = CallerContext.de(user, email, roles);

        List<Appointment> encontradas = caller.soloVeLoPropio()
                ? service.searchDelPaciente(caller.email(), status, from, to)
                : service.search(status, from, to);

        return encontradas.stream().map(Response::from).toList();
    }

    @GetMapping("/{id}")
    public Response get(@PathVariable Long id,
                        @RequestHeader(value = "X-User", required = false) String user,
                        @RequestHeader(value = "X-User-Email", required = false) String email,
                        @RequestHeader(value = "X-Roles", required = false) String roles) {

        CallerContext caller = CallerContext.de(user, email, roles);
        Appointment atencion = service.get(id);

        if (caller.soloVeLoPropio() && !caller.esDuenoDe(atencion.getPatientEmail())) {
            // 404 y no 403: revelar que la atencion existe ya es informacion.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe la atencion " + id);
        }

        return Response.from(atencion);
    }

    /**
     * Un paciente solo puede agendar para si mismo: el email se toma del token,
     * ignorando lo que venga en el cuerpo de la peticion.
     */
    @PostMapping
    public ResponseEntity<Response> create(@Valid @RequestBody CreateRequest request,
                                           @RequestHeader(value = "X-User", required = false) String user,
                                           @RequestHeader(value = "X-User-Email", required = false) String email,
                                           @RequestHeader(value = "X-Roles", required = false) String roles) {

        CallerContext caller = CallerContext.de(user, email, roles);

        CreateRequest efectiva = caller.soloVeLoPropio() && !caller.email().isBlank()
                ? new CreateRequest(request.patientName(), caller.email(),
                        request.serviceId(), request.scheduledAt())
                : request;

        Response creada = Response.from(service.create(efectiva, caller.email()));
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    /** Cambiar el estado es tarea del personal, no del paciente. */
    @PutMapping("/{id}/status")
    public Response changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request,
                                 @RequestHeader(value = "X-User", required = false) String user,
                                 @RequestHeader(value = "X-User-Email", required = false) String email,
                                 @RequestHeader(value = "X-Roles", required = false) String roles) {

        CallerContext caller = CallerContext.de(user, email, roles);

        if (caller.soloVeLoPropio()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo el personal del centro puede cambiar el estado de una atencion.");
        }

        return Response.from(service.changeStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestHeader(value = "X-User", required = false) String user,
                                       @RequestHeader(value = "X-User-Email", required = false) String email,
                                       @RequestHeader(value = "X-Roles", required = false) String roles) {

        CallerContext caller = CallerContext.de(user, email, roles);

        if (caller.soloVeLoPropio()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo el personal del centro puede eliminar una atencion.");
        }

        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
