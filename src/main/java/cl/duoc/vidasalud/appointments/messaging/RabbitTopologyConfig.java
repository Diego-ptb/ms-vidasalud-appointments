package cl.duoc.vidasalud.appointments.messaging;

import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia de RabbitMQ del dominio de atenciones, declarada como beans.
 *
 * Spring AMQP la crea en el broker al arrancar (idempotente: si ya existe, no
 * hace nada). Aqui no hay nada de negocio y en el negocio no hay nada de
 * configuracion: esa separacion es deliberada.
 *
 * Cada microservicio declara los exchanges que usa y SOLO las colas que el
 * mismo consume. El duenio de una cola es su consumidor, no quien publica:
 *
 *   - appointments (este)  -> q.admision.ticket
 *   - ms-vidasalud-catalog -> q.catalogo.cupo
 *   - ms-vidasalud-notifications -> q.notificaciones.email
 *
 * Asi un servicio puede desplegarse solo y su cola existe, sin depender de que
 * otro haya arrancado antes.
 */
@Configuration
public class RabbitTopologyConfig {

    private final MessagingProperties nombres;

    public RabbitTopologyConfig(MessagingProperties nombres) {
        this.nombres = nombres;
    }

    // ------------------------------------------------------------- exchanges

    /**
     * Exchange de eventos. Es topic porque un evento tiene varios interesados
     * que el productor no conoce: la notificacion al paciente y la generacion
     * del ticket escuchan el mismo "atencion.confirmada" por su cuenta.
     */
    @Bean
    TopicExchange exchangeAtenciones() {
        return new TopicExchange(nombres.getExchanges().getTopic(), true, false);
    }

    /**
     * Exchange de comandos. Es direct porque un comando tiene un destinatario
     * unico y conocido: "descuenta este cupo" va al catalogo y a nadie mas.
     */
    @Bean
    DirectExchange exchangeComandos() {
        return new DirectExchange(nombres.getExchanges().getDirect(), true, false);
    }

    /** Exchange al que RabbitMQ reencamina lo que los consumidores rechazan. */
    @Bean
    DirectExchange exchangeDlx() {
        return new DirectExchange(nombres.getExchanges().getDlx(), true, false);
    }

    // ----------------------------------------------------- cola de admision

    /**
     * Cola de generacion de tickets de admision.
     *
     * Los dos argumentos x-dead-letter-* son lo que convierte un rechazo en un
     * mensaje en la DLQ en vez de un mensaje perdido.
     */
    @Bean
    Queue colaTicket() {
        String cola = nombres.getQueues().getTicket();
        return QueueBuilder.durable(cola)
                .withArguments(Map.of(
                        "x-dead-letter-exchange", nombres.getExchanges().getDlx(),
                        "x-dead-letter-routing-key", nombres.getDlq().getTicket()))
                .build();
    }

    @Bean
    Queue colaTicketDlq() {
        return QueueBuilder.durable(nombres.getDlq().getTicket()).build();
    }

    /** La cola de tickets escucha cualquier evento de atencion (atencion.#). */
    @Bean
    Binding bindingTicket() {
        return BindingBuilder.bind(colaTicket())
                .to(exchangeAtenciones())
                .with(nombres.getRouting().getTicketPattern());
    }

    @Bean
    Binding bindingTicketDlq() {
        return BindingBuilder.bind(colaTicketDlq())
                .to(exchangeDlx())
                .with(nombres.getDlq().getTicket());
    }
}
