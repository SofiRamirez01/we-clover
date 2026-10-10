package com.weclover.backend.dto.tanda;

import jakarta.validation.constraints.NotBlank;

/** El formato (solo letras) y la unicidad entre tandas abiertas se validan en TandaService. */
public record TandaRenombrarRequest(

    @NotBlank(message = "Debe indicar el nombre de la tanda")
    String nombre
) {
}
