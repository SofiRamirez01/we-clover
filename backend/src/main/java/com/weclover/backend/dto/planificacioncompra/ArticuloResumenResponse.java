package com.weclover.backend.dto.planificacioncompra;

import com.weclover.backend.entity.UnidadMedida;

/**
 * Resumen unificado (CAMBIO 5): cantidad total de un (TipoTela, PaletaColores) a lo largo de
 * toda una PlanificacionCompra, más el estimado en pesos según el proveedor preferido de ese
 * color (ArticuloProveedor.preferido) — null si ningún proveedor fue marcado como preferido
 * para ese color, o si el que lo era está dado de baja.
 */
public record ArticuloResumenResponse(
    Long idTipoTela,
    String tipoTela,
    String nombreTipoTela,
    Long idPaletaColor,
    String nombreColor,
    String hexColor,
    float cantidad,
    UnidadMedida unidadMedida,
    String nombreProveedorPreferido,
    Float precioUnitarioEstimado,
    Float estimadoTotal
) {
}
