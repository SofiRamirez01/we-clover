package com.weclover.backend.dto.pieza;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SegmentoRectaDto(
    @NotNull String tipo,
    @NotNull @Size(min = 2, max = 2) double[] puntoInicial,
    @NotNull @Size(min = 2, max = 2) double[] puntoFinal
) implements SegmentoDto {
}
