package com.weclover.backend.dto.planificacioncompra;

import com.weclover.backend.entity.UnidadMedida;

/**
 * Resumen unificado (CAMBIO 5): cantidad total de un (TipoTela, PaletaColores) a lo largo de
 * toda una PlanificacionCompra, más el estimado en pesos según el proveedor preferido de ese
 * color (ArticuloProveedor.preferido) — null si ningún proveedor fue marcado como preferido
 * para ese color, o si el que lo era está dado de baja.
 *
 * Fase 2 (integración con Stock): {@code stockDisponible} es la suma de todas las filas de
 * `Stock` de ese color (todos los proveedores juntos, ver StockRepository.findByArticulo) al
 * momento de pedir el resumen — 0 si el color nunca se auditó. {@code cantidadAComprar} es
 * {@code max(0, cantidadNecesaria - stockDisponible)}. Puramente informativo: nada de esto
 * modifica el registro de Stock, ver PlanificacionCompraService.obtenerResumen.
 * {@code precioUnitarioEstimado}/{@code estimadoTotal} se calculan sobre cantidadAComprar (no
 * sobre cantidadNecesaria) — decisión tomada con el usuario: el estimado en pesos debe reflejar
 * lo que realmente hace falta gastar, no lo que costaría producir todo desde cero.
 */
public record ArticuloResumenResponse(
    Long idTipoTela,
    String tipoTela,
    String nombreTipoTela,
    Long idPaletaColor,
    String nombreColor,
    String hexColor,
    float cantidadNecesaria,
    float stockDisponible,
    float cantidadAComprar,
    UnidadMedida unidadMedida,
    String nombreProveedorPreferido,
    Float precioUnitarioEstimado,
    Float estimadoTotal
) {
}
