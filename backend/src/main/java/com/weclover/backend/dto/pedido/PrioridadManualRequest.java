package com.weclover.backend.dto.pedido;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PrioridadManualRequest(

    @NotNull(message = "Debe indicar la prioridad")
    @Positive(message = "La prioridad debe ser mayor a cero")
    Integer prioridad
) {
}
