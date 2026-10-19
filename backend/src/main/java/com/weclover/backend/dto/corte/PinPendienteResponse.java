package com.weclover.backend.dto.corte;

/** Un pin que no se pudo resolver, o una pieza sin geometría, dentro de un pendiente. */
public record PinPendienteResponse(
    /** Null cuando el pendiente es de una pieza sin geometría y no de un pin puntual. */
    Long idPosicionPieza,
    String etiqueta,
    Long idPieza,
    String nombrePieza
) {
}
