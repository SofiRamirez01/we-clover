package com.weclover.backend.dto.producto;

import jakarta.validation.constraints.NotNull;

/** Un pin de la moldería del producto y la fila de insumo de la ficha que lo cubre. */
public record AsignacionInsumoPiezaItemRequest(

    @NotNull(message = "Falta indicar el pin de la moldería")
    Long idPosicionPieza,

    @NotNull(message = "Falta indicar la fila de insumo")
    Long idProductoInsumo
) {
}
