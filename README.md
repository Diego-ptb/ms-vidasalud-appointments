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

Este servicio **no habla AMQP**. Su `pom.xml` no incluye el starter de RabbitMQ y no
contiene ninguna clase de mensajería: toda la mensajería vive en
`ms-vidasalud-rabbit-admin`.

Al confirmar o cancelar una atención, anuncia el hecho por HTTP:

```
POST {mensajeria}/api/mq/events/atencion-confirmada
```

y es ese servicio quien lo convierte en mensajes de RabbitMQ. Aquí solo existe la
interfaz `AtencionEventos`, que el dominio usa sin saber qué hay detrás, y su
implementación `MensajeriaClient`.

Dos decisiones que conviene conocer:

**El aviso se manda después del commit, no dentro de la transacción.** Si se mandara
dentro, un consumidor rápido podría leer la base antes de que el cambio sea visible y
procesar el estado viejo. Es el problema de la escritura dual, y falla de forma
intermitente según quién gane la carrera. Si la transacción termina en rollback, el
aviso no se manda: no se anuncia un hecho que no ocurrió.

**Un fallo al avisar no voltea la operación.** Si el servicio de mensajería está caído,
la atención ya quedó confirmada en la base y eso vale; se registra el error y se sigue.

### Endpoint interno de ticket

| Método | Ruta | Quién llama |
|---|---|---|
| `PUT` | `/api/appointments/{id}/ticket` | ms-vidasalud-rabbit-admin |

Lo invoca el consumidor de `q.admision.ticket`. Emite el ticket y lo guarda en la columna
`TICKET_CODE` — la prueba visible de que el mensaje se procesó, porque no la escribe
ninguna petición de usuario.

La emisión es **idempotente**: si la atención ya tiene ticket, no se genera otro. Hace
falta porque RabbitMQ garantiza "al menos una entrega", no "exactamente una".

Responde `409` si la atención no admite ticket y `404` si no existe. El consumidor
interpreta cualquier 4xx como error definitivo y manda el mensaje a la DLQ.

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
