package com.weclover.backend.dto.pedido;

import com.weclover.backend.entity.EstadoPedido;

/** Contexto del pedido guardado en el momento de un cambio de tanda (ver
 *  HistorialTandaPedido) — no se recalcula. */
public record SnapshotAsignacionTandaResponse(
    float porcentajePagado,
    Integer prioridadAutomatica,
    EstadoPedido estadoPedido,
    boolean disenoCompleto,
    boolean tallesCompletos,
    boolean pagoSuficiente
) {
}
