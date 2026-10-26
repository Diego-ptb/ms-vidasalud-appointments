package cl.duoc.vidasalud.appointments.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion del cliente AMQP: serializacion y confirmaciones de publicacion.
 * Igual que la topologia, vive aparte del codigo de negocio.
 */
@Configuration
public class RabbitClientConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitClientConfig.class);

    /**
     * JSON en vez de serializacion Java: el mensaje queda legible en la
     * Management UI y los consumidores no necesitan las mismas clases.
     */
    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate con confirmaciones activadas.
     *
     * Publicar no es gratis: si el exchange no existe o el broker no acepta el
     * mensaje, hay que enterarse. Sin esto el publish parece exitoso siempre.
     */
    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setMandatory(true);

        template.setConfirmCallback((correlation, ack, causa) -> {
            if (!ack) {
                log.error("RabbitMQ no confirmo la publicacion del mensaje {}: {}",
                        correlation != null ? correlation.getId() : "sin-id", causa);
            }
        });

        // Se dispara cuando el mensaje llega al exchange pero ninguna cola
        // calza con la routing key: senal de topologia mal configurada.
        template.setReturnsCallback(returned -> log.error(
                "Mensaje devuelto por el broker. exchange={} routingKey={} motivo={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));

        return template;
    }
}
