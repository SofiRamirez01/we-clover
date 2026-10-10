package com.weclover.backend.dto.pedido;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NotaPedidoRequest(

    @NotBlank(message = "La nota no puede estar vacía")
    @Size(max = 1000, message = "La nota no puede superar los 1000 caracteres")
    String texto
) {
}
