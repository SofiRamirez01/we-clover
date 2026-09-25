package com.weclover.backend.entity;

/**
 * PRESUPUESTADO, SENADO, ENTREGADO y CANCELADO los setea una persona (ver PedidoService).
 * LISTO_PARA_PRODUCCION, EN_PRODUCCION y TERMINADO los calcula el sistema
 * (EstadoPedidoService.recalcularEstadoPedido), nunca sobre un pedido ENTREGADO/CANCELADO, y
 * nunca retroceden.
 */
public enum EstadoPedido {
    PRESUPUESTADO,
    SENADO,
    LISTO_PARA_PRODUCCION,
    EN_PRODUCCION,
    TERMINADO,
    ENTREGADO,
    CANCELADO
}
