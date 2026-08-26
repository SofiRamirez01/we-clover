package com.weclover.backend.dto.producto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductoColorItemRequest(

    @NotNull(message = "Falta indicar la posición del patrón de corte")
    Long idPatronCorteColor,

    @NotNull(message = "Debe seleccionar el color de la paleta")
    Long idPaletaColor,

    Integer coordenadaX,

    Integer coordenadaY,

    @Size(max = 20, message = "El RGB detectado no puede superar los 20 caracteres")
    String rgbDetectado
) {
}
