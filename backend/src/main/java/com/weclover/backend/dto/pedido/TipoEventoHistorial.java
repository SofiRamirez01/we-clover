package com.weclover.backend.dto.pedido;

/** Discrimina las dos fuentes que se combinan en el historial unificado de un pedido (ver
 *  PedidoService.listarHistorial): cambios de EstadoPedido y cambios de etapa de producción. */
public enum TipoEventoHistorial {
    ESTADO_PEDIDO,
    ETAPA_PRODUCCION
}
