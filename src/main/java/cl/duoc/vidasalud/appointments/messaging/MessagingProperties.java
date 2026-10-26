package cl.duoc.vidasalud.appointments.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de la topologia de mensajeria, leidos del bloque
 * {@code vidasalud.messaging} de application.yml.
 *
 * Es la unica fuente de los nombres de colas, exchanges y routing keys: ni los
 * servicios de dominio ni los consumidores escriben literales. Asi renombrar
 * una cola es editar la configuracion, no buscar strings por el proyecto.
 */
@ConfigurationProperties(prefix = "vidasalud.messaging")
public class MessagingProperties {

    private Exchanges exchanges = new Exchanges();
    private Queues queues = new Queues();
    private Dlq dlq = new Dlq();
    private Routing routing = new Routing();

    public static class Exchanges {
        private String direct = "vidasalud.comandos.direct";
        private String topic = "vidasalud.atenciones.topic";
        private String dlx = "vidasalud.dlx";

        public String getDirect() {
            return direct;
        }

        public void setDirect(String direct) {
            this.direct = direct;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getDlx() {
            return dlx;
        }

        public void setDlx(String dlx) {
            this.dlx = dlx;
        }
    }

    public static class Queues {
        private String notificaciones = "q.notificaciones.email";
        private String ticket = "q.admision.ticket";
        private String cupo = "q.catalogo.cupo";

        public String getNotificaciones() {
            return notificaciones;
        }

        public void setNotificaciones(String notificaciones) {
            this.notificaciones = notificaciones;
        }

        public String getTicket() {
            return ticket;
        }

        public void setTicket(String ticket) {
            this.ticket = ticket;
        }

        public String getCupo() {
            return cupo;
        }

        public void setCupo(String cupo) {
            this.cupo = cupo;
        }
    }

    /** Dead letter queue de cada cola de trabajo. */
    public static class Dlq {
        private String notificaciones = "q.notificaciones.email.dlq";
        private String ticket = "q.admision.ticket.dlq";
        private String cupo = "q.catalogo.cupo.dlq";

        public String getNotificaciones() {
            return notificaciones;
        }

        public void setNotificaciones(String notificaciones) {
            this.notificaciones = notificaciones;
        }

        public String getTicket() {
            return ticket;
        }

        public void setTicket(String ticket) {
            this.ticket = ticket;
        }

        public String getCupo() {
            return cupo;
        }

        public void setCupo(String cupo) {
            this.cupo = cupo;
        }
    }

    public static class Routing {
        private String atencionConfirmada = "atencion.confirmada";
        private String atencionCancelada = "atencion.cancelada";
        private String ticketPattern = "atencion.#";
        private String cupoTomar = "cupo.tomar";

        public String getAtencionConfirmada() {
            return atencionConfirmada;
        }

        public void setAtencionConfirmada(String atencionConfirmada) {
            this.atencionConfirmada = atencionConfirmada;
        }

        public String getAtencionCancelada() {
            return atencionCancelada;
        }

        public void setAtencionCancelada(String atencionCancelada) {
            this.atencionCancelada = atencionCancelada;
        }

        public String getTicketPattern() {
            return ticketPattern;
        }

        public void setTicketPattern(String ticketPattern) {
            this.ticketPattern = ticketPattern;
        }

        public String getCupoTomar() {
            return cupoTomar;
        }

        public void setCupoTomar(String cupoTomar) {
            this.cupoTomar = cupoTomar;
        }
    }

    public Exchanges getExchanges() {
        return exchanges;
    }

    public void setExchanges(Exchanges exchanges) {
        this.exchanges = exchanges;
    }

    public Queues getQueues() {
        return queues;
    }

    public void setQueues(Queues queues) {
        this.queues = queues;
    }

    public Dlq getDlq() {
        return dlq;
    }

    public void setDlq(Dlq dlq) {
        this.dlq = dlq;
    }

    public Routing getRouting() {
        return routing;
    }

    public void setRouting(Routing routing) {
        this.routing = routing;
    }

}
