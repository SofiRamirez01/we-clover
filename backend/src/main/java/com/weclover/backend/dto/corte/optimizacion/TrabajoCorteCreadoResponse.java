package com.weclover.backend.dto.corte.optimizacion;

/** Trabajo de optimización encolado: con `idTrabajo` se consulta el avance y el resultado. */
public record TrabajoCorteCreadoResponse(
    String idTrabajo,
    Long idTanda,
    String estado,
    Integer posicionEnCola
) {
}
