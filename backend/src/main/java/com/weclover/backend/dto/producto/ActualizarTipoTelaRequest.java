package com.weclover.backend.dto.producto;

import jakarta.validation.constraints.NotBlank;

public record ActualizarTipoTelaRequest(

    @NotBlank(message = "Debe indicar el tipo de tela")
    String tipoTela
) {
}
