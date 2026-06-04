package edu.udla.integration.progreso2.controller;

import edu.udla.integration.progreso2.model.CitaRequest;
import edu.udla.integration.progreso2.service.CitaValidationService;
import org.apache.camel.ProducerTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/citas")
public class CitaController {

    private final ProducerTemplate producerTemplate;
    private final CitaValidationService validationService;

    public CitaController(ProducerTemplate producerTemplate, CitaValidationService validationService) {
        this.producerTemplate = producerTemplate;
        this.validationService = validationService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> registrarCita(@RequestBody CitaRequest cita) {

        Map<String, Object> response = new HashMap<>();

        if (!validationService.esValida(cita)) {
            validationService.registrarError(cita.getIdCita(), "Datos inválidos o incompletos", cita.toString());
            response.put("estado", "RECHAZADA");
            response.put("motivo", "Datos inválidos o incompletos");
            return ResponseEntity.badRequest().body(response);
        }

        producerTemplate.sendBody("direct:procesarCita", cita);

        response.put("estado", "ACEPTADA");
        response.put("idCita", cita.getIdCita());
        response.put("mensaje", "Cita registrada y en proceso de integración");
        return ResponseEntity.ok(response);
    }
}