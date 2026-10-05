package com.weclover.backend.dto.reportes;

/** Proyección de una query agregada por tipo de prenda (ver ReporteVentasRepository). */
public record UnidadesMontoPorTipoPrenda(
    Long idTipoPrenda,
    String tipoPrenda,
    Long unidades,
    Double monto
) {
}
