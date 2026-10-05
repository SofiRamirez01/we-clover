package com.weclover.backend.dto.reportes;

/** monto solo incluye pedidos con precio por prenda (ver ReporteVentasResponse.pedidosSinDesglose). */
public record VentasPorTipoPrendaResponse(
    Long idTipoPrenda,
    String tipoPrenda,
    long unidades,
    double monto
) {
}
