package com.weclover.backend.dto.piezatalle;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/**
 * El backend NO recalcula ni escala nada acá: todas las estadísticas (área/ancho/largo/
 * perímetro) ya vienen calculadas del lado del frontend (escalarPieza para talles graduados,
 * el mismo shoelace/perímetro que usa el editor para talles corregidos a mano). Este endpoint
 * solo valida que las coordenadas no estén vacías y que el talle pertenezca al grupo de la
 * Pieza, y persiste tal cual.
 */
public record PiezaTalleUpsertRequest(
    @NotEmpty List<double[]> coordenadas,
    double areaCm2,
    double anchoCm,
    double largoCm,
    double perimetroCm,
    boolean esBase,
    boolean editadoManualmente
) {
}
