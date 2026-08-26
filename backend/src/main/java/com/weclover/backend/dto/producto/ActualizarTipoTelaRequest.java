package com.weclover.backend.dto.producto;

import com.weclover.backend.entity.TipoTela;

import jakarta.validation.constraints.NotNull;

public record ActualizarTipoTelaRequest(

    @NotNull(message = "Debe indicar el tipo de tela")
    TipoTela tipoTela
) {
}
