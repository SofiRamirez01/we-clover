package com.weclover.backend.dto.corte;

import java.util.List;

/**
 * Totales de la preparación. `unidadesPendientes` cuenta cada unidad una sola vez, aunque tenga
 * varios motivos; en `pendientesPorMotivo` una misma unidad puede figurar bajo más de un motivo
 * (ver `notaPendientesPorMotivo`), así que esos totales no se suman.
 */
public record ResumenCorteResponse(
    int unidadesIncluidas,
    int unidadesPendientes,
    /** Piezas distintas (por id) que aparecen en la demanda. */
    int piezasDistintas,
    List<PendientesPorMotivoResponse> pendientesPorMotivo,
    String notaPendientesPorMotivo,
    List<FaltanteCargaCorteResponse> faltaCargar
) {
}
