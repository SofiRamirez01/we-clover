package com.weclover.backend.dto.pedido;

import java.time.LocalDateTime;

import com.weclover.backend.entity.EstadoPedido;

public record HistorialEstadoPedidoResponse(
    Long id,
    EstadoPedido estado,
    LocalDateTime fechaCambio,
    String observaciones,
    String nombreUsuario,
    String emailUsuario
) {
}
