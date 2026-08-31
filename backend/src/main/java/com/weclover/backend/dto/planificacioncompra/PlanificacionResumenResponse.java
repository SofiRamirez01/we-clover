package com.weclover.backend.dto.planificacioncompra;

/** Referencia liviana a una PlanificacionCompra, para el badge "Ya en Planificación #X". */
public record PlanificacionResumenResponse(
    Long id,
    String nombre
) {
}
