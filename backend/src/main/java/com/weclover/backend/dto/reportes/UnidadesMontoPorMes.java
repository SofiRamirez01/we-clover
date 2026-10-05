package com.weclover.backend.dto.reportes;

/** Proyección de una query agregada por mes (ver ReporteVentasRepository). */
public record UnidadesMontoPorMes(
    Integer anio,
    Integer mes,
    Long unidades,
    Double monto
) {
}
