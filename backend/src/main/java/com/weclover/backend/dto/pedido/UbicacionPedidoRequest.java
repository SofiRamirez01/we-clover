package com.weclover.backend.dto.pedido;

import jakarta.validation.constraints.Size;

/** Null o en blanco = el pedido queda sin ubicación cargada. */
public record UbicacionPedidoRequest(

    @Size(max = 255, message = "La ubicación no puede superar los 255 caracteres")
    String ubicacion
) {
}
