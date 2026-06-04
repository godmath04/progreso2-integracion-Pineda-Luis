package edu.udla.integration.progreso2.routes;

import edu.udla.integration.progreso2.model.CitaRequest;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

@Component
public class CitaIntegrationRoute extends RouteBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void configure() throws Exception {

        // Ruta principal
        from("direct:procesarCita")
                .routeId("ruta-principal")
                .process(exchange -> exchange.setProperty("cita", exchange.getIn().getBody(CitaRequest.class)))
                .log("Cita recibida: ${body}")
                .to("direct:enviarFacturacion")
                .to("direct:publicarEvento")
                .to("direct:generarCSV");

        // 1. Point-to-Point que es billing.queue
        from("direct:enviarFacturacion")
                .routeId("ruta-facturacion")
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
                .to("spring-rabbitmq:default?queues=billing.queue")
                .log("Enviado a billing.queue");

        // 2. Publish/Subscribe que es appointments.events
        from("direct:publicarEvento")
                .routeId("ruta-pubsub")
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
                .to("spring-rabbitmq:appointments.events?exchangeType=fanout")
                .log("Evento publicado en appointments.events");

        // 3. Archivo CSV que seria para sistema legado
        from("direct:generarCSV")
                .routeId("ruta-csv")
                .process(exchange -> {
                    CitaRequest cita = exchange.getProperty("cita", CitaRequest.class);
                    java.io.File file = new java.io.File("data/outbox/auditoria-citas.csv");
                    file.getParentFile().mkdirs();
                    boolean escribirHeader = !file.exists() || file.length() == 0;
                    try (FileWriter fw = new FileWriter(file, true)) {
                        if (escribirHeader) {
                            fw.write("idCita,paciente,correo,especialidad,fechaCita,sede,valor\n");
                        }
                        fw.write(String.join(",",
                                cita.getIdCita(),
                                cita.getPaciente(),
                                cita.getCorreo(),
                                cita.getEspecialidad(),
                                cita.getFechaCita(),
                                cita.getSede(),
                                String.valueOf(cita.getValor())
                        ) + "\n");
                    }
                })
                .log("Línea agregada al CSV de auditoría");
    }
}