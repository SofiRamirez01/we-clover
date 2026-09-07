package com.weclover.backend.dto.pieza;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SegmentoCirculoDto(
    @NotNull String tipo,
    @NotNull @Size(min = 2, max = 2) double[] centro,
    @Positive double radioCm,
    double anguloInicial,
    double anguloFinal
) implements SegmentoDto {
}
