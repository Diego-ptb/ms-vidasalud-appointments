package cl.duoc.vidasalud.appointments.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Los microservicios de dominio viven en la red privada y solo deben ser
 * llamados por el BFF. Este filtro exige la clave compartida X-Internal-Key,
 * de modo que nadie pueda saltarse la cadena API Gateway -> BFF.
 */
@Component
public class InternalKeyFilter extends OncePerRequestFilter {

    @Value("${vidasalud.internal-key:vidasalud-internal}")
    private String expectedKey;

    @Value("${vidasalud.internal-key-enabled:true}")
    private boolean enabled;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        String key = request.getHeader("X-Internal-Key");
        if (expectedKey.equals(key)) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":401,\"error\":\"unauthorized\",\"message\":\"Llamada no proviene del BFF\"}");
    }
}
