package com.weclover.backend.dto.producto;

/**
 * Un pin de la moldería cuya pieza se corta en un insumo secundario. `etiqueta` es el texto a
 * mostrar (la etiqueta del pin o el nombre de la pieza, numerado si se repite) e
 * `idProductoInsumo` la fila de la ficha asignada explícitamente, o null si no tiene.
 */
public record PinInsumoResponse(
    Long idPosicionPieza,
    String etiqueta,
    Long idPieza,
    String nombrePieza,
    Long idProductoInsumo
) {
}
