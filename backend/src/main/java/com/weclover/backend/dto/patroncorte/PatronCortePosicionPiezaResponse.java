package com.weclover.backend.dto.patroncorte;

import com.weclover.backend.dto.pieza.PiezaResumenResponse;

public record PatronCortePosicionPiezaResponse(
    Long id,
    double coordenadaXPin,
    double coordenadaYPin,
    String etiqueta,
    PiezaResumenResponse pieza,
    /** Solo al editar un pin: cuántas asignaciones de insumo de fichas se descartaron porque
     *  el pin pasó a una pieza de otro tipo de insumo. Null en las lecturas. */
    Integer asignacionesDescartadas
) {
}
