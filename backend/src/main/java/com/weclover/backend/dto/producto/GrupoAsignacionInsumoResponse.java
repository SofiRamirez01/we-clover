package com.weclover.backend.dto.producto;

import java.util.List;

/**
 * Un tipo de insumo que tiene pines en la moldería del producto. `requiereAsignacion` es true
 * cuando el producto tiene dos o más filas de ese tipo (`idsProductoInsumo`): recién ahí cada
 * pin debe marcarse en una fila, y `pinesSinAsignar` cuenta los que faltan.
 */
public record GrupoAsignacionInsumoResponse(
    Long idTipoInsumo,
    String codigoTipoInsumo,
    String nombreTipoInsumo,
    boolean requiereAsignacion,
    List<Long> idsProductoInsumo,
    List<PinInsumoResponse> pines,
    int pinesSinAsignar
) {
}
