package com.weclover.backend.dto.corte;

import java.util.List;

/** Todo lo que se corta en un mismo tipo de tela. */
public record GrupoTelaCorteResponse(
    Long idTipoTela,
    String codigoTipoTela,
    String nombreTipoTela,
    List<ItemCorteResponse> items
) {
}
