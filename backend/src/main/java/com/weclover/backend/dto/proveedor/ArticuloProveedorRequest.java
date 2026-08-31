package com.weclover.backend.dto.proveedor;

import com.weclover.backend.entity.UnidadMedida;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ArticuloProveedorRequest(

    @NotNull(message = "Debe indicar el color de la carta")
    Long idPaletaColor,

    @NotNull(message = "Debe indicar la unidad de medida")
    UnidadMedida unidadMedida,

    @PositiveOrZero(message = "El precio estimado no puede ser negativo")
    Float precioEstimado,

    boolean preferido
) {
}
