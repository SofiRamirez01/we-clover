package com.weclover.backend.dto.pedido;

import java.time.LocalDateTime;

import com.weclover.backend.entity.EstadoPedido;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CambioEstadoRequest(

    @NotNull(message = "Debe indicar el nuevo estado del pedido")
    EstadoPedido estado,

    @NotNull(message = "Debe indicar la fecha del cambio de estado")
    LocalDateTime fechaCambio,

    @Size(max = 500, message = "Las observaciones no pueden superar los 500 caracteres")
    String observaciones
) {
}
