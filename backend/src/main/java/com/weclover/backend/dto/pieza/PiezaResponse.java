package com.weclover.backend.dto.pieza;

public record PiezaResponse(
    Long id,
    String nombre,
    Long idGrupoTalle,
    String nombreGrupoTalle,
    Long idTalleBase,
    String talleBase,
    boolean simetrica,
    double anchoBaseCm,
    double largoBaseCm,
    boolean activo,
    /** Tipo de insumo en el que se corta la pieza; los dos en null si es del cuerpo. */
    Long idTipoInsumo,
    String nombreTipoInsumo
) {
}
