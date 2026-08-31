package com.weclover.backend.dto.proveedor;

import jakarta.validation.constraints.NotNull;

public record CambiarActivoRequest(
    @NotNull(message = "Debe indicar el nuevo estado")
    Boolean activo
) {
}
