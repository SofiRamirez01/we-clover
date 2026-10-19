package com.weclover.backend.dto.corte;

/**
 * Trazabilidad de un ítem: qué parte de su cantidad viene de qué producto y pedido, en qué
 * color, y de qué fila de insumo de la ficha (null si la pieza es del cuerpo).
 */
public record OrigenItemCorteResponse(
    Long idPedido,
    String codigoInternoPedido,
    Long idProducto,
    Long idPaletaColor,
    int cantidad,
    Long idProductoInsumo
) {
}
