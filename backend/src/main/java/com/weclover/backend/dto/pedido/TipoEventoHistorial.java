package com.weclover.backend.dto.pedido;

/** Discrimina los tipos de fila del historial unificado de un pedido (ver
 *  PedidoService.listarHistorial): cambios de EstadoPedido, marcados de etapa de producción, y
 *  cambios de estado de producción de una prenda que no vienen de una etapa (pedido ENTREGADO,
 *  recálculo, carga retroactiva). */
public enum TipoEventoHistorial {
    ESTADO_PEDIDO,
    ETAPA_PRODUCCION,
    ESTADO_PRODUCCION
}
