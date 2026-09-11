package com.weclover.backend.dto.piezatalle;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record PiezaTalleBatchItemRequest(
    @NotNull Long idTalle,
    @NotEmpty List<double[]> coordenadas,
    double areaCm2,
    double anchoCm,
    double largoCm,
    double perimetroCm,
    boolean editadoManualmente
) {
}
