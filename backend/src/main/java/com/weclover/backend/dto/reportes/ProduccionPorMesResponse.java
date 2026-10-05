package com.weclover.backend.dto.reportes;

/** mes en formato YYYY-MM. Los meses sin movimientos del período vienen igual, en 0. */
public record ProduccionPorMesResponse(
    String mes,
    long unidades
) {
}
