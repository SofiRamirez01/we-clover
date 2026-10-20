package com.weclover.backend.dto.pedido;

import com.weclover.backend.entity.EstadoPedido;

/** Proyección de PedidoRepository.sumarUnidadesPorEstadoYTipoPrenda (contadores del listado). */
public record UnidadesPorEstadoYTipoPrenda(EstadoPedido estado, String tipoPrenda, Long unidades) {
}
