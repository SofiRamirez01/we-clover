package com.weclover.backend.dto.producto;

import java.util.List;

/**
 * Asignaciones pin → fila de insumo de un producto, agrupadas por tipo de insumo.
 * `pinesSinAsignar` (total del producto) es un aviso: no vuelve incompleta a la ficha.
 */
public record AsignacionesInsumoResponse(
    Long idProducto,
    List<GrupoAsignacionInsumoResponse> grupos,
    int pinesSinAsignar
) {
}
