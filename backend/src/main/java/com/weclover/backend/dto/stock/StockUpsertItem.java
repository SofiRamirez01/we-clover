package com.weclover.backend.dto.stock;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record StockUpsertItem(

    @NotNull(message = "Debe indicar el color de la carta")
    Long idPaletaColor,

    /** Null = fila "sin proveedor" (ver Stock.proveedor). */
    Long idProveedor,

    @PositiveOrZero(message = "La cantidad no puede ser negativa")
    float cantidad
) {
}
