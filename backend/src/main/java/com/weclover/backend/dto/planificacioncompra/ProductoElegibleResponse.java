package com.weclover.backend.dto.planificacioncompra;

import java.time.LocalDate;
import java.util.List;

import com.weclover.backend.dto.produccion.ProduccionTandaResponse;
import com.weclover.backend.dto.producto.ProductoResponse;
import com.weclover.backend.entity.EstadoPedido;

/**
 * Producto candidato a incluir en una PlanificacionCompra, con el contexto de su pedido de
 * origen (para filtros/columnas de la pantalla) y su elegibilidad ya resuelta server-side
 * (ver ProductoService.motivoDisenoIncompleto) — el frontend no reevalúa esta regla, solo la
 * muestra.
 */
public record ProductoElegibleResponse(
    ProductoResponse producto,
    Long idPedido,
    String codigoInternoPedido,
    String nombreColegio,
    EstadoPedido estadoPedido,
    /** Tanda del pedido (null = sin tanda). Se filtra por `id`, nunca por nombre. */
    ProduccionTandaResponse tanda,
    LocalDate fechaVentaPedido,
    LocalDate fechaEstimadaEntregaPedido,
    float porcentajePagadoPedido,
    boolean disenoCompleto,
    String motivoIncompleto,
    /** Null si el producto no tiene moldería asignada (ahí ya va a estar disenoCompleto=false). */
    String nombreMolderia,
    Integer numeroInternoMolderia,
    List<PlanificacionResumenResponse> planificacionesQueLoIncluyen
) {
}
