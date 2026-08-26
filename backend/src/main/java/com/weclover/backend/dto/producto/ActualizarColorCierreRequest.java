package com.weclover.backend.dto.producto;

import jakarta.validation.constraints.NotNull;

public record ActualizarColorCierreRequest(

    @NotNull(message = "Debe seleccionar el color del cierre")
    Long idPaletaColor
) {
}
