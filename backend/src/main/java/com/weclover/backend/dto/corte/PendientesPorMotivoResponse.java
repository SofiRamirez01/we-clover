package com.weclover.backend.dto.corte;

/** Cuántos pendientes y cuántas unidades hay bajo un motivo. */
public record PendientesPorMotivoResponse(
    MotivoPendienteCorte motivo,
    int pendientes,
    int unidades
) {
}
