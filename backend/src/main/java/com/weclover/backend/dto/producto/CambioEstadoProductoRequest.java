package com.weclover.backend.dto.producto;

import com.weclover.backend.entity.EstadoPedido;

import jakarta.validation.constraints.NotNull;

public record CambioEstadoProductoRequest(

    @NotNull(message = "Debe indicar el nuevo estado de la prenda")
    EstadoPedido estado
) {
}
