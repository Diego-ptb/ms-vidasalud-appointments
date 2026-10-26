package cl.duoc.vidasalud.appointments.domain;

/**
 * Salida de eventos del dominio de atenciones.
 *
 * El dominio declara QUE hechos anuncia; no sabe por donde viajan. La
 * implementacion que publica en RabbitMQ vive en el paquete messaging, de modo
 * que la logica de negocio no importa ni una clase de AMQP. Cambiar de
 * mensajeria, o quitarla, no toca este paquete.
 */
public interface AtencionEventos {

    void confirmada(Appointment atencion);

    void cancelada(Appointment atencion);
}
