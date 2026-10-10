package com.weclover.backend.dto.tanda;

/** Total de unidades de un tipo de prenda dentro de una tanda (suma de Producto.cantidadTotal). */
public record UnidadesPorTipoPrendaResponse(
    String tipoPrenda,
    int unidades
) {
}
