package com.weclover.backend.dto.producto;

import jakarta.validation.constraints.NotNull;

public record MarcarEtapaRequest(

    @NotNull(message = "Debe indicar si la etapa se completa o se revierte")
    Boolean completado,

    Long idEmpleado
) {
}
