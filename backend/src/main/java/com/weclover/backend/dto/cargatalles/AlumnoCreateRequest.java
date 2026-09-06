package com.weclover.backend.dto.cargatalles;

import jakarta.validation.constraints.NotBlank;

public record AlumnoCreateRequest(

    @NotBlank(message = "Ingresá el nombre del alumno")
    String nombreAlumno
) {
}
