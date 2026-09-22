package cl.duoc.vidasalud.appointments.web;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Convierte los errores de validacion en mensajes que el frontend pueda mostrar
 * tal cual al usuario. Sin esto, Spring responde
 * "Validation failed for object='createRequest'. Error count: 1", que no le
 * dice nada a quien esta usando la aplicacion.
 */
@RestControllerAdvice
public class ValidationExceptionHandler {

    /** Traduce el nombre tecnico del campo al que ve el usuario en pantalla. */
    private static final Map<String, String> CAMPOS = Map.of(
            "patientName", "el nombre del paciente",
            "patientEmail", "el email del paciente",
            "serviceId", "la prestacion",
            "scheduledAt", "la fecha y hora",
            "status", "el estado");

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(ValidationExceptionHandler::describir)
                .distinct()
                .collect(Collectors.joining(" "));

        return respuesta(HttpStatus.BAD_REQUEST, "validation",
                detalle.isBlank() ? "Revisa los datos del formulario." : detalle);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> cuerpoIlegible(HttpMessageNotReadableException ex) {
        // El detalle tecnico queda en el log del servicio, no en la respuesta.
        return respuesta(HttpStatus.BAD_REQUEST, "bad_request",
                "Los datos enviados no tienen el formato esperado.");
    }

    private static String describir(org.springframework.validation.FieldError error) {
        String campo = CAMPOS.getOrDefault(error.getField(), error.getField());

        return switch (String.valueOf(error.getCode())) {
            case "Future" -> "La atencion debe agendarse en una fecha y hora futura.";
            case "NotBlank", "NotNull" -> "Falta " + campo + ".";
            case "Email" -> "El email del paciente no es valido.";
            default -> "Revisa " + campo + ".";
        };
    }

    private static ResponseEntity<Map<String, Object>> respuesta(HttpStatus status, String codigo,
                                                                 String mensaje) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", Instant.now().toString());
        cuerpo.put("status", status.value());
        cuerpo.put("error", codigo);
        cuerpo.put("message", mensaje);
        return ResponseEntity.status(status).body(cuerpo);
    }
}
