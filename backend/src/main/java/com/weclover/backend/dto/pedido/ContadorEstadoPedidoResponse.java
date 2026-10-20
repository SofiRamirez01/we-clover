package com.weclover.backend.dto.pedido;

import java.util.Map;

import com.weclover.backend.entity.EstadoPedido;

/**
 * Totales de un estado para el tablero de Base Ventas (ver ListadoPedidosResponse.contadores).
 *
 * @param unidadesPorTipoPrenda unidades vendidas (Producto.cantidadTotal de las prendas
 *                              habilitadas) por nombre de tipo de prenda
 */
public record ContadorEstadoPedidoResponse(
    EstadoPedido estado,
    long pedidos,
    Map<String, Long> unidadesPorTipoPrenda
) {
}
