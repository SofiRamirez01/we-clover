package com.weclover.backend.dto.patroncorte;

import com.weclover.backend.dto.pieza.PiezaResumenResponse;

public record PatronCortePosicionPiezaResponse(
    Long id,
    double coordenadaXPin,
    double coordenadaYPin,
    String etiqueta,
    PiezaResumenResponse pieza
) {
}
