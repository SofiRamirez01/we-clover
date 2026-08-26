package com.weclover.backend.dto.paletacolores;

import com.weclover.backend.entity.TipoTela;

public record PaletaColorResponse(
    Long id,
    String nombre,
    String hex,
    TipoTela tipoTela,
    boolean activo
) {
}
