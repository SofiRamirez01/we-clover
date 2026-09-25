package com.weclover.backend.dto.producto;

import com.weclover.backend.entity.EstadoBandera;

import jakarta.validation.constraints.NotNull;

public record MarcarEstadoBanderaRequest(

    @NotNull(message = "Debe indicar el nuevo estado de la bandera")
    EstadoBandera estadoBandera
) {
}
