package com.weclover.backend.dto.pedido;

import java.util.List;

/**
 * Una página del listado de Base Ventas (ver PedidoListadoService).
 *
 * @param total      pedidos que cumplen todos los filtros (no solo los de esta página)
 * @param contadores pedidos y unidades por estado de la promoción y rango de fechas elegidos,
 *                   sin aplicar el filtro de estado ni la búsqueda: así los totales del tablero
 *                   no quedan en 0 cuando se ocultan los entregados. Trae todos los estados (con
 *                   0 los que no tienen pedidos); el frontend los agrupa (BUCKET_POR_ESTADO).
 */
public record ListadoPedidosResponse(
    List<PedidoResponse> pedidos,
    long total,
    int pagina,
    int tamanio,
    boolean hayMas,
    List<ContadorEstadoPedidoResponse> contadores
) {
}
