package cl.duoc.vidasalud.appointments.messaging;

/**
 * Fallo transitorio al procesar un mensaje: la base no responde, un servicio
 * esta reiniciando. El mensaje es valido, asi que vale la pena reintentarlo.
 *
 * La distincion importa: un mensaje mal formado reintentado mil veces no se
 * arregla solo y tapa la cola. Esta excepcion marca la diferencia entre
 * devolver el mensaje a la cola (NACK con requeue) y mandarlo a la DLQ.
 */
public class ErrorRecuperable extends RuntimeException {

    public ErrorRecuperable(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
