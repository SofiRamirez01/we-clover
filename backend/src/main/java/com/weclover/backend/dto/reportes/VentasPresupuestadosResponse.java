package com.weclover.backend.dto.reportes;

/** Pedidos todavía en PRESUPUESTADO (no vendidos) del período, con los mismos filtros. */
public record VentasPresupuestadosResponse(
    long cantidadPedidos,
    long unidades,
    double monto,
    boolean montoIncompleto
) {
}
