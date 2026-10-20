package com.weclover.backend.dto.corte.optimizacion;

/** Avance de un trabajo: corte, corrida y generación en curso, y lo mejor del corte en curso. */
public record ProgresoCorteResponse(
    Integer corteActual,
    Integer cortesTotales,
    Integer corridaActual,
    Long semillaActual,
    Integer generacionActual,
    Integer generacionesTotales,
    Double mejorLargoCm,
    Double mejorEficiencia,
    Double segundosTranscurridos
) {
}
