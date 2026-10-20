package com.weclover.backend.dto.corte.optimizacion;

import java.util.Map;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Opciones de una optimización (todas opcionales; sin cuerpo se usan los valores del servicio).
 * `modoTiempo`: RAPIDO (hasta 60 s por corte) o COMPLETO (hasta 200 s). `excedenteMaximoPorTela`
 * va por código de tela (ej. {"RIBB": 2}) y pisa a `excedenteMaximoPorItem` en esa tela.
 */
public record OptimizarCorteRequest(
    @Pattern(regexp = "RAPIDO|COMPLETO", message = "modoTiempo tiene que ser RAPIDO o COMPLETO")
    String modoTiempo,
    @Positive Integer presupuestoSegundosPorCorte,
    @PositiveOrZero Integer excedenteMaximoPorItem,
    Map<String, @PositiveOrZero Integer> excedenteMaximoPorTela
) {
}
