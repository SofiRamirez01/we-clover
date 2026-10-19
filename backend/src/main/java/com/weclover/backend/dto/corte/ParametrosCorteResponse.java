package com.weclover.backend.dto.corte;

/** Copia de los parámetros del corte con los que se armó la preparación (ver CorteProperties). */
public record ParametrosCorteResponse(
    double anchoUtilCm,
    double largoMaximoCm,
    /** No está aplicado a las coordenadas de `piezas`: lo aplica el paso siguiente. */
    double margenCorteCm,
    int capasMaximas,
    int telasPorCapa
) {
}
