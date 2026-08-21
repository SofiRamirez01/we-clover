package com.weclover.backend.dto.producto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductoCreateRequest(

    @NotNull(message = "Debe seleccionar el tipo de prenda")
    Long idTipoPrenda,

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
