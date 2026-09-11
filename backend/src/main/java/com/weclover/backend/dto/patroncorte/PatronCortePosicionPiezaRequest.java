package com.weclover.backend.dto.patroncorte;

import jakarta.validation.constraints.NotNull;

/** Mismo shape para alta (POST) y edición (PUT): PUT reemplaza pieza asignada + posición del
 * pin + etiqueta por completo, no hace falta un patch parcial. */
public record PatronCortePosicionPiezaRequest(
    @NotNull Long piezaId,
    double coordenadaXPin,
    double coordenadaYPin,
    String etiqueta
) {
}
