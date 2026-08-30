package com.weclover.backend.dto.tipotela;

public record TipoTelaResponse(
    Long id,
    String codigo,
    String nombre,
    boolean esPorPeso,
    boolean telaCuerpo,
    Integer gramosSugerido
) {
}
