package edu.udla.integration.progreso2.service;

import edu.udla.integration.progreso2.model.CitaRequest;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class CitaValidationService {

    private static final String ERRORS_PATH = "data/errors/citas-rechazadas.log";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public boolean esValida(CitaRequest cita) {
        return cita.getIdCita() != null && !cita.getIdCita().isBlank()
                && cita.getPaciente() != null && !cita.getPaciente().isBlank()
                && cita.getCorreo() != null && !cita.getCorreo().isBlank()
                && cita.getEspecialidad() != null && !cita.getEspecialidad().isBlank()
                && cita.getFechaCita() != null && !cita.getFechaCita().isBlank()
                && cita.getSede() != null && !cita.getSede().isBlank()
                && cita.getValor() != null && cita.getValor() > 0;
    }

    public void registrarError(String idCita, String motivo, String payload) {
        try {
            java.io.File file = new java.io.File(ERRORS_PATH);
            file.getParentFile().mkdirs();

            try (FileWriter fw = new FileWriter(file, true)) {
                fw.write("---\n");
                fw.write("Fecha: " + LocalDateTime.now().format(FORMATTER) + "\n");
                fw.write("idCita: " + (idCita != null ? idCita : "N/A") + "\n");
                fw.write("Motivo: " + motivo + "\n");
                fw.write("Payload: " + payload + "\n");
            }
        } catch (IOException e) {
            System.err.println("Error al escribir log: " + e.getMessage());
        }
    }
}
