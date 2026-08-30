package com.weclover.backend.dto.producto;

public record ProductoInsumoSecundarioResponse(
    Long id,
    String descripcion,
    Long idTipoTela,
    String tipoTela,
    String nombreTipoTela,
    boolean esPorPeso,
    Long idPaletaColor,
    String nombreColor,
    String hexColor,
    float cantidad
) {
}
