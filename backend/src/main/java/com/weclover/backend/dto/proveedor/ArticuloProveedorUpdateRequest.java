package com.weclover.backend.dto.proveedor;

import com.weclover.backend.entity.UnidadMedida;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ArticuloProveedorUpdateRequest(

    @NotNull(message = "Debe indicar la unidad de medida")
    UnidadMedida unidadMedida,

    @PositiveOrZero(message = "El precio estimado no puede ser negativo")
    Float precioEstimado,

    boolean preferido,

    boolean activo
) {
}
