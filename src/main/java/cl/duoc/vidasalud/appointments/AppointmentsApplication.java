package cl.duoc.vidasalud.appointments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import cl.duoc.vidasalud.appointments.messaging.MessagingProperties;

@SpringBootApplication
@EnableConfigurationProperties(MessagingProperties.class)
public class AppointmentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppointmentsApplication.class, args);
    }
}
