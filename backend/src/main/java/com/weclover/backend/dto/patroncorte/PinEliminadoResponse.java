package com.weclover.backend.dto.patroncorte;

/** Resultado de borrar un pin: cuántas asignaciones de insumo de fichas se descartaron con él. */
public record PinEliminadoResponse(int asignacionesDescartadas) {
}
