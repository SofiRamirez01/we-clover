package com.weclover.backend.dto.producto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record ProductoColoresRequest(

    @NotEmpty(message = "Debe indicar el color de al menos una posición")
    List<@Valid ProductoColorItemRequest> colores
) {
}
