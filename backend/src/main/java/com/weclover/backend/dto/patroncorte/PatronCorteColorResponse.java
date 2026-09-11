package com.weclover.backend.dto.patroncorte;

import java.util.List;

public record PatronCorteColorResponse(
    Long id,
    int orden,
    int gramos,
    List<PatronCortePosicionPiezaResponse> piezas
) {
}
