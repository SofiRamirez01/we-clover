package com.weclover.backend.dto.paletacolores;

public record PaletaColorResponse(
    Long id,
    String nombre,
    String hex,
    String tipoTela,
    boolean activo
) {
}
