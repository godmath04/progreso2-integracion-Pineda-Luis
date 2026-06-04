# Progreso 2 - Integración de Sistemas

**Estudiante:** Luis Pineda  
**Asignatura:** Integración de Sistemas  
**Universidad:** UDLA

---

## Descripción de la solución

Lo siguiente es una solución de integración para la organización **Salud360**. Esta integration permite la 
automatización del flujo de registro de citas médicas entre sistemas desconectados. Una API REST recibe la solicitud, la valida, y mediante Apache Camel la distribuye a tres destinos:

- Sistema de Facturación (mensajería Point-to-Point)
- Sistemas de Notificaciones y Analítica (mensajería Publish/Subscribe)
- Sistema Legado de Auditoría (archivo CSV)

---

## Tecnologías utilizadas

- Java 17
- Spring Boot 3.5.0
- Apache Camel 4.8.5
- RabbitMQ 3.12 (broker de mensajería)
- Docker / Docker Compose
- SpringDoc OpenAPI (Swagger UI)
- Lombok

---

## Instrucciones para levantar RabbitMQ

Requiere Docker instalado.

```bash
docker-compose up -d
```

RabbitMQ quedará disponible en:

- AMQP: `localhost:5672`
- Management UI: http://localhost:15672
  - Usuario: `admin`
  - Contraseña: `admin123`

Para detenerlo:

```bash
docker-compose down
```

### Creación automática de colas y exchanges

Las colas y el exchange se crean automáticamente al iniciar la aplicación mediante `RabbitAdmin`. No es necesario crearlos manualmente en la UI de RabbitMQ.

```java
@Bean
public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
    return new RabbitAdmin(connectionFactory);
}

@Bean public Queue billingQueue()        { return new Queue("billing.queue", true); }
@Bean public Queue notificationsQueue()  { return new Queue("notifications.queue", true); }
@Bean public Queue analyticsQueue()      { return new Queue("analytics.queue", true); }

@Bean
public FanoutExchange appointmentsExchange() {
    return new FanoutExchange("appointments.events");
}
```

---

## Instrucciones para ejecutar la aplicación

```bash
./mvnw spring-boot:run
```

La aplicación inicia en: http://localhost:8080

Swagger UI disponible en: http://localhost:8080/swagger-ui/index.html

> RabbitMQ debe estar corriendo antes de iniciar la aplicación.

---

## Endpoint disponible

| Método | Ruta        | Descripción                        |
|--------|-------------|------------------------------------|
| POST   | /api/citas  | Registra una solicitud de cita médica |

---

## Ejemplo de request válido

```json
{
  "idCita": "CITA-1001",
  "paciente": "Ana Torres",
  "correo": "ana.torres@email.com",
  "especialidad": "Cardiología",
  "fechaCita": "2026-06-15",
  "sede": "Centro Norte",
  "valor": 45.50
}
```

**Respuesta esperada (200 OK):**

```json
{
  "idCita": "CITA-1001",
  "estado": "ACEPTADA",
  "mensaje": "Cita registrada y en proceso de integración"
}
```

---

## Ejemplo de request inválido

```json
{
  "idCita": "",
  "paciente": "Ana Torres",
  "correo": "ana.torres@email.com",
  "especialidad": "Cardiología",
  "fechaCita": "2026-06-15",
  "sede": "Centro Norte",
  "valor": 0
}
```

**Respuesta esperada (400 Bad Request):**

```json
{
  "estado": "RECHAZADA",
  "motivo": "Datos inválidos o incompletos"
}
```

---

## Explicación de patrones de integración

### Point-to-Point Channel
Aplicado en el envío al **sistema de facturación**. La cita válida genera un comando `COMANDO_FACTURAR_CITA` que se publica en la cola `billing.queue`. Solo un consumidor (el sistema de facturación) debe procesar este mensaje, garantizando que la orden de cobro se genere una única vez.

```java
from("direct:enviarFacturacion")
    .process(exchange -> {
        CitaRequest cita = exchange.getProperty("cita", CitaRequest.class);
        Map<String, Object> mensaje = new HashMap<>();
        mensaje.put("idCita", cita.getIdCita());
        mensaje.put("paciente", cita.getPaciente());
        mensaje.put("especialidad", cita.getEspecialidad());
        mensaje.put("valor", cita.getValor());
        mensaje.put("tipoMensaje", "COMANDO_FACTURAR_CITA");
        exchange.getIn().setBody(objectMapper.writeValueAsString(mensaje));
    })
    .to("spring-rabbitmq:default?routingKey=billing.queue");
```

### Publish/Subscribe Channel
Aplicado para **notificar a múltiples sistemas** ante una cita confirmada. El evento `CITA_CONFIRMADA` se publica en el exchange fanout `appointments.events`, que lo distribuye simultáneamente a:
- `notifications.queue` → Sistema de Notificaciones (envía mensaje al paciente)
- `analytics.queue` → Sistema de Analítica

```java
from("direct:publicarEvento")
    .process(exchange -> {
        CitaRequest cita = exchange.getProperty("cita", CitaRequest.class);
        Map<String, Object> evento = new HashMap<>();
        evento.put("idCita", cita.getIdCita());
        evento.put("paciente", cita.getPaciente());
        evento.put("correo", cita.getCorreo());
        evento.put("especialidad", cita.getEspecialidad());
        evento.put("fechaCita", cita.getFechaCita());
        evento.put("sede", cita.getSede());
        evento.put("tipoEvento", "CITA_CONFIRMADA");
        exchange.getIn().setBody(objectMapper.writeValueAsString(evento));
    })
    .to("spring-rabbitmq:appointments.events?exchangeType=fanout");
```

### Transferencia de archivos
Aplicado para integrar con el **sistema legado de auditoría**, que no tiene API ni conexión a mensajería. Por cada cita válida se escribe una línea en el archivo `data/outbox/auditoria-citas.csv`.

```java
from("direct:generarCSV")
    .process(exchange -> {
        CitaRequest cita = exchange.getProperty("cita", CitaRequest.class);
        File file = new File("data/outbox/auditoria-citas.csv");
        file.getParentFile().mkdirs();
        boolean escribirHeader = !file.exists() || file.length() == 0;
        try (FileWriter fw = new FileWriter(file, true)) {
            if (escribirHeader) {
                fw.write("idCita,paciente,correo,especialidad,fechaCita,sede,valor\n");
            }
            fw.write(String.join(",", cita.getIdCita(), cita.getPaciente(),
                cita.getCorreo(), cita.getEspecialidad(),
                cita.getFechaCita(), cita.getSede(),
                String.valueOf(cita.getValor())) + "\n");
        }
    });
```

### Manejo de errores
Si la solicitud contiene datos inválidos o incompletos, la API responde con `400 RECHAZADA` y registra el error en `data/errors/citas-rechazadas.log` con fecha, idCita, motivo y payload recibido.

```java
if (!validationService.esValida(cita)) {
    validationService.registrarError(cita.getIdCita(), "Datos inválidos o incompletos", cita.toString());
    response.put("estado", "RECHAZADA");
    response.put("motivo", "Datos inválidos o incompletos");
    return ResponseEntity.badRequest().body(response);
}
```

---

## Evidencia de funcionamiento

Para verificar el funcionamiento completo:

1. Levantar RabbitMQ con `docker-compose up -d`
2. Iniciar la aplicación con `./mvnw spring-boot:run`
3. Enviar un POST válido a `/api/citas` desde Swagger o Postman
4. Verificar en RabbitMQ Management (http://localhost:15672):
   - `billing.queue` contiene 1 mensaje
   - `notifications.queue` contiene 1 mensaje
   - `analytics.queue` contiene 1 mensaje
5. Verificar que `data/outbox/auditoria-citas.csv` tiene una nueva línea
6. Enviar un POST inválido y verificar que `data/errors/citas-rechazadas.log` registra el error
