package com.weclover.backend.dto.producto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record MarcarEtapasBulkRequest(

    @NotEmpty(message = "Debe indicar al menos una etapa a actualizar")
    List<@Valid EtapaUpdateDTO> etapas
) {
}
