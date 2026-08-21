package com.weclover.backend.dto.producto;

public record ProductoResponse(
    Long id,
    Long idTipoPrenda,
    String tipoPrenda,
    int cantidadTotal,
    float costo,
    float subtotal,
    String observaciones,
    String imagenDisenoUrl
) {
}
