package com.weclover.backend.dto.corte;

/** Cuántas piezas de un ítem van en un color: es el dato de las capas de ese color. */
public record CantidadPorColorResponse(
    Long idPaletaColor,
    String nombreColor,
    String hex,
    int cantidad
) {
}
