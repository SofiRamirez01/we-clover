package com.weclover.backend.dto.producto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductoCreateRequest(

    @NotBlank(message = "El tipo de prenda es obligatorio")
    @Size(max = 100, message = "El tipo de prenda no puede superar los 100 caracteres")
    String tipoPrenda,

    @Positive(message = "La cantidad debe ser mayor a cero")
    int cantidadTotal,

    @PositiveOrZero(message = "El costo no puede ser negativo")
    float costo,

    @Size(max = 500, message = "Las observaciones no pueden superar los 500 caracteres")
    String observaciones,

    @Size(max = 500, message = "La URL de la imagen no puede superar los 500 caracteres")
    String imagenDisenoUrl
) {
}
