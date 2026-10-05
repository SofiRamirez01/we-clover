package com.weclover.backend.dto.reportes;

import com.weclover.backend.entity.EstadoProduccion;

/** Proyección de la foto actual de producción agrupada por estado (ver ReporteProduccionRepository). */
public record UnidadesPorEstado(
    EstadoProduccion estado,
    Long unidades
) {
}
