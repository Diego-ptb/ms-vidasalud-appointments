# ms-vidasalud-appointments

Microservicio de **atenciones** del caso VidaSalud · DSY1107 Desarrollo Cloud Native I.

Gestiona el ciclo de vida de una atención y es dueño exclusivo de su base de datos. No se
expone a internet: solo recibe llamadas de `ms-vidasalud-bff`, que ya validó el JWT.

## Máquina de estados

```
SOLICITADA ──► CONFIRMADA ──► EN_ESPERA ──► EN_ATENCION ──► CERRADA
     │              │              │
     └──────────────┴──────────────┴──────► CANCELADA
```

La regla del caso —*no se puede pasar a EN_ATENCIÓN sin CONFIRMAR*— se hace cumplir en
`AppointmentStatus.canMoveTo`. Un salto inválido responde **409 Conflict** indicando qué
transiciones sí están permitidas. Al confirmar se asigna el box.

## Reglas de negocio

- Una atención solo puede agendarse en una **fecha futura** (`@Future` en el DTO y
  verificación en el servicio).
- Un **paciente solo ve y agenda lo suyo**: la lista se filtra por su correo, pedir la
  atención de otro devuelve 404, y al crear se fuerza el email del token ignorando lo que
  venga en el cuerpo.
- Cambiar estados y eliminar son operaciones **solo del personal** (ADMIN, RECEPCIONISTA).

La identidad llega en las cabeceras `X-User`, `X-User-Email` y `X-Roles` que el BFF extrae
del token ya validado; nunca de un parámetro del cliente.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/appointments` | Lista, con filtros `status`, `from`, `to` |
| `GET` | `/api/appointments/{id}` | Detalle |
| `POST` | `/api/appointments` | Crear |
| `PUT` | `/api/appointments/{id}/status` | Cambiar estado |
| `DELETE` | `/api/appointments/{id}` | Eliminar |
| `GET` | `/api/report/kpis?range=last24h` | KPIs de operación |
| `GET` | `/api/report/top-services?range=last7d` | Prestaciones más demandadas |
| `GET` | `/api/audit/timeline` | Trazabilidad, solo lectura |

> **Alcance EP1:** `report` y `audit` viven aquí. En la siguiente etapa se separan en
> `ms-vidasalud-report` y `ms-vidasalud-audit`, consumiendo el tópico Kafka
> `appointments.events`.

## Mensajería

Al confirmar o cancelar una atención, el servicio **publica un evento** y sigue; el
trabajo lento lo hacen los consumidores. Si el broker está caído la atención ya quedó
guardada y eso vale: se registra el fallo y la operación de negocio no se voltea.

### Lo que publica

| Hecho | Exchange | Tipo | Routing key |
|---|---|---|---|
| Atención confirmada | `vidasalud.atenciones.topic` | topic | `atencion.confirmada` |
| Atención cancelada | `vidasalud.atenciones.topic` | topic | `atencion.cancelada` |
| Descontar un cupo | `vidasalud.comandos.direct` | direct | `cupo.tomar` |

Los dos primeros son **hechos**: quien los reciba decide qué hacer. El tercero es una
**orden** con un destinatario único (`ms-vidasalud-catalog`), y por eso va por un
exchange direct y no por el topic.

El dominio no conoce RabbitMQ: `AppointmentService` depende de la interfaz
`AtencionEventos`, y la implementación que publica vive en el paquete `messaging`.

### Lo que consume

`q.admision.ticket`, ligada a `atencion.#`: emite el ticket de admisión y lo guarda en la
columna `TICKET_CODE`. Esa columna es la prueba visible de que el mensaje se procesó,
porque no la escribe ninguna llamada HTTP.

La emisión es **idempotente**: si la atención ya tiene ticket, no se genera otro.
RabbitMQ garantiza "al menos una entrega", no "exactamente una", así que un mensaje
reintentado puede llegar dos veces.

### ACK y errores

ACK manual, con tres salidas claras:

| Situación | Decisión |
|---|---|
| Ticket emitido | `basicAck` |
| Evento que no es confirmación | `basicAck` — no es un error, no hay ticket que emitir |
| Atención inexistente o en mal estado | `basicNack` sin requeue → DLQ |
| Fallo transitorio, 1ª vez | `basicNack` con requeue |
| Fallo transitorio, 2ª vez | `basicNack` sin requeue → DLQ |

Lo que cae en `q.admision.ticket.dlq` se consume y se registra con nivel `error`, para
que quede rastro de la atención que quedó sin ticket.

## Persistencia

Entidad `Appointment` → tabla `vs_appointment`, vía Spring Data JPA. Hibernate crea el
esquema al arrancar (`ddl-auto: update`).

Dos perfiles:

| Perfil | Base |
|---|---|
| `dev` (por defecto) | H2 en memoria, para desarrollar sin dependencias |
| `supabase` | PostgreSQL gestionado en Supabase |

Los filtros opcionales usan **Specifications** y no JPQL con `:param is null`, porque
PostgreSQL no puede inferir el tipo de un parámetro nulo.

## Ejecutar en local

Requiere **JDK 17+**.

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

mvn spring-boot:run                 # perfil dev, H2 en memoria
```

Contra PostgreSQL:

```powershell
$env:SPRING_PROFILES_ACTIVE = "supabase"
$env:DB_APPOINTMENTS_URL = "jdbc:postgresql://<host>.pooler.supabase.com:5432/postgres"
$env:DB_APPOINTMENTS_USER = "postgres.<project-ref>"
$env:DB_APPOINTMENTS_PASSWORD = "<password>"
$env:INTERNAL_KEY = "<clave compartida con el BFF>"

mvn spring-boot:run
```

Queda en `http://localhost:8081`.

> Usa el **Session pooler** de Supabase (puerto 5432). El Transaction pooler (6543) rompe
> los prepared statements de Hibernate salvo que agregues `?prepareThreshold=0`.

## Variables de entorno

| Variable | Descripción |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` o `supabase` |
| `DB_APPOINTMENTS_URL` · `_USER` · `_PASSWORD` | Conexión a PostgreSQL |
| `INTERNAL_KEY` | Clave compartida; sin ella toda llamada responde 401 |
| `INTERNAL_KEY_ENABLED` | `false` para desactivar la comprobación en desarrollo |

## Stack

Java 17 · Spring Boot 3.3.5 · Spring Data JPA · PostgreSQL / H2 · Maven · Docker
