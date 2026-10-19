package com.weclover.backend.dto.corte;

import java.util.List;

/**
 * Una pieza en un talle, dentro de una tela: se dibuja una sola vez y se corta en capas de
 * todos sus colores (el color no agrupa). `cantidadTotal` es la suma de `porColor`.
 */
public record ItemCorteResponse(
    Long idPieza,
    Long idTalle,
    String talle,
    List<CantidadPorColorResponse> porColor,
    int cantidadTotal,
    List<OrigenItemCorteResponse> origen
) {
}
