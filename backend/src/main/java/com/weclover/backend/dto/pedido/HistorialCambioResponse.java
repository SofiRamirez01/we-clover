package com.weclover.backend.dto.pedido;

import java.time.LocalDateTime;

import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.EstadoProduccion;
import com.weclover.backend.entity.EtapaProduccion;

/**
 * Fila del historial unificado de un pedido (ver PedidoService.listarHistorial), que combina
 * HistorialEstadoPedido (cambios de EstadoPedido) y MovimientoEstado (cambios de etapa y de
 * estado de producción por prenda) en una sola línea de tiempo, ordenada por fechaCambio desc.
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

    /** Estado de producción de la prenda antes/después del movimiento — solo para
     *  ETAPA_PRODUCCION/ESTADO_PRODUCCION; null en filas migradas del historial viejo. */
    EstadoProduccion estadoProduccionAnterior,
    EstadoProduccion estadoProduccionNuevo,

    /** Quién hizo el cambio. Null en transiciones automáticas de ESTADO_PEDIDO (ver
     *  EstadoPedidoService) y en ESTADO_PRODUCCION sin actor (carga retroactiva, recálculo por
     *  cambio de etapas aplicables) — un marcado de etapa siempre tiene un actor humano. */
    String nombreUsuario,
    String emailUsuario,

    /** Solo para ASIGNACION_TANDA (null en el resto). id/nombre null = "sin tanda"; el nombre
     *  es el que tenía la tanda en ese momento. El motivo viaja en `observaciones`. */
    Long idTandaAnterior,
    String nombreTandaAnterior,
    Long idTandaNueva,
    String nombreTandaNueva,
    /** Null si el cambio no vino del popup de priorización (salida por cancelación). */
    Long idSesionPriorizacion,
    SnapshotAsignacionTandaResponse snapshotTanda,

    /** Solo para UBICACION (null en el resto). null = sin ubicación cargada. */
    String ubicacionAnterior,
    String ubicacionNueva
) {

    /** Fila de cualquier tipo que no sea ASIGNACION_TANDA ni UBICACION: esos campos van null. */
    public HistorialCambioResponse(
            Long id, LocalDateTime fechaCambio, TipoEventoHistorial tipoEvento,
            EstadoPedido estadoPedido, String observaciones,
            String tipoPrenda, EtapaProduccion etapa, Boolean etapaCompletado, String nombreEmpleadoAsignado,
            EstadoProduccion estadoProduccionAnterior, EstadoProduccion estadoProduccionNuevo,
            String nombreUsuario, String emailUsuario) {
        this(id, fechaCambio, tipoEvento, estadoPedido, observaciones, tipoPrenda, etapa, etapaCompletado,
            nombreEmpleadoAsignado, estadoProduccionAnterior, estadoProduccionNuevo, nombreUsuario, emailUsuario,
            null, null, null, null, null, null, null, null);
    }
}
