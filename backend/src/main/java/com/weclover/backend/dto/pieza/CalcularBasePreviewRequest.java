package com.weclover.backend.dto.pieza;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/** Cuerpo de POST /api/piezas/calcular-base (proxy de solo cálculo, no persiste nada). */
public record CalcularBasePreviewRequest(
    @NotEmpty @Valid List<SegmentoDto> segmentos,
    boolean simetrica
) {
}
