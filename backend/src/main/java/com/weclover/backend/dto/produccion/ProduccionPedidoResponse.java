package com.weclover.backend.dto.produccion;

import java.time.LocalDate;
import java.util.List;

import com.weclover.backend.entity.EstadoPedido;

public record ProduccionPedidoResponse(
    Long id,
    String codigoInterno,
    String colegio,
    String curso,
    EstadoPedido estadoActual,
    LocalDate fechaVenta,
    LocalDate fechaEstimadaEntrega,
    float porcentajePagado,
    /** Null si el pedido no está activo (ENTREGADO/CANCELADO) — ver EstadoPedidoService. */
    Integer prioridadAutomatica,
    Integer prioridadManual,
    List<ProduccionProductoResponse> productos
) {
}
