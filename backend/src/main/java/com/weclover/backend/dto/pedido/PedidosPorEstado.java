package com.weclover.backend.dto.pedido;

import com.weclover.backend.entity.EstadoPedido;

/** Proyección de PedidoRepository.contarPorEstado (contadores del listado de Base Ventas). */
public record PedidosPorEstado(EstadoPedido estado, Long pedidos) {
}
