package com.weclover.backend.dto.reportes;

/** mes en formato YYYY-MM. Los meses sin ventas del período vienen igual, en 0. */
public record VentasPorMesResponse(
    String mes,
    long unidades,
    double monto
) {
}
