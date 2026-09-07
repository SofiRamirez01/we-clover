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
    boolean activo
) {
}
