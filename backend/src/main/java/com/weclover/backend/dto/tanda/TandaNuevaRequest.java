package com.weclover.backend.dto.tanda;

import jakarta.validation.constraints.NotBlank;

/** Tanda a crear en la sesión. `idTemporal` lo inventa el cliente y solo sirve para que el
 *  resto del mismo request (orden, movimientos) pueda referenciarla antes de que tenga id. */
public record TandaNuevaRequest(

    @NotBlank(message = "Cada tanda nueva debe tener un identificador temporal")
    String idTemporal,

    @NotBlank(message = "Debe indicar el nombre de la tanda")
    String nombre
) {
}
