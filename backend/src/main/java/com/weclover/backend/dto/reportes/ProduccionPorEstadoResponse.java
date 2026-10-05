package com.weclover.backend.dto.reportes;

import com.weclover.backend.entity.EstadoProduccion;

public record ProduccionPorEstadoResponse(
    EstadoProduccion estado,
    long unidades
) {
}
