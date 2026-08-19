package com.weclover.backend.dto.producto;

public record ProductoResponse(
    Long id,
    String tipoPrenda,
    int cantidadTotal,
    float costo,
    float subtotal,
    String observaciones,
    String imagenDisenoUrl
) {
}
