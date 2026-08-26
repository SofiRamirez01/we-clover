package com.weclover.backend.dto.producto;

import com.weclover.backend.entity.EstadoPedido;

public record ProductoResponse(
    Long id,
    Long idTipoPrenda,
    String tipoPrenda,
    int cantidadTotal,
    float costo,
    float subtotal,
    String observaciones,
    String imagenDisenoUrl,
    EstadoPedido estadoActual
) {
}
