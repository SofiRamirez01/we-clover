package com.weclover.backend.dto.pieza;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SegmentoArcoDto(
    @NotNull String tipo,
    @NotNull @Size(min = 2, max = 2) double[] puntoInicial,
    @NotNull @Size(min = 2, max = 2) double[] puntoFinal,
    @Positive double radioCm,
    @Pattern(regexp = "IZQUIERDA|DERECHA") String lado
) implements SegmentoDto {
}
