package com.weclover.backend.dto.proveedor;

import com.weclover.backend.entity.UnidadMedida;

public record ArticuloProveedorResponse(
    Long id,
    Long idProveedor,
    String nombreProveedor,
    Long idPaletaColor,
    String nombreColor,
    String hexColor,
    String tipoTela,
    UnidadMedida unidadMedida,
    Float precioEstimado,
    boolean preferido,
    boolean activo
) {
}
