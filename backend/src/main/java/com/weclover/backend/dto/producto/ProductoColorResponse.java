package com.weclover.backend.dto.producto;

import com.weclover.backend.entity.MetodoDeteccionColor;

public record ProductoColorResponse(
    Long id,
    Long idPatronCorteColor,
    int ordenPatronCorteColor,
    Long idPaletaColor,
    String nombreColor,
    String hexColor,
    MetodoDeteccionColor metodoDeteccion,
    Integer coordenadaX,
    Integer coordenadaY,
    String rgbDetectado
) {
}
