package edu.udla.integration.progreso2.model;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CitaRequest {

    @NotBlank(message = "idCita es obligatorio")
    private String idCita;

    @NotBlank(message = "paciente es obligatorio")
    private String paciente;

    @NotBlank(message = "correo es obligatorio")
    @Email(message = "correo debe tener formato válido")
    private String correo;

    @NotBlank(message = "especialidad es obligatoria")
    private String especialidad;

    @NotBlank(message = "fechaCita es obligatoria")
    private String fechaCita;

    @NotBlank(message = "sede es obligatoria")
    private String sede;

    @NotNull(message = "valor es obligatorio")
    @DecimalMin(value = "0.01", message = "valor debe ser mayor a 0")
    private Double valor;
}