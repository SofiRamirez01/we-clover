package com.weclover.backend.dto.pedido;

import java.time.LocalDateTime;

import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.EtapaProduccion;

/**
 * Fila del historial unificado de un pedido (ver PedidoService.listarHistorial), que combina
 * HistorialEstadoPedido (cambios de EstadoPedido) e HistorialEtapaProduccion (cambios de etapa
 * de producción por prenda) en una sola línea de tiempo, ordenada por fechaCambio desc.
 *
 * Campos de EstadoPedido (estadoPedido/observaciones) van null cuando tipoEvento=
 * ETAPA_PRODUCCION; campos de etapa (tipoPrenda/etapa/etapaCompletado/nombreEmpleadoAsignado)
 * van null cuando tipoEvento=ESTADO_PEDIDO.
 */
public record HistorialCambioResponse(
    Long id,
    LocalDateTime fechaCambio,
    TipoEventoHistorial tipoEvento,

    EstadoPedido estadoPedido,
    String observaciones,

    String tipoPrenda,
    EtapaProduccion etapa,
    Boolean etapaCompletado,
    String nombreEmpleadoAsignado,

    /** Quién hizo el cambio. Null solo es posible para ESTADO_PEDIDO (transición automática, ver
     *  EstadoPedidoService) — un cambio de etapa siempre tiene un actor humano. */
    String nombreUsuario,
    String emailUsuario
) {
}
