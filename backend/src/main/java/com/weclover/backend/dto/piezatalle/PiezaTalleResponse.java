package com.weclover.backend.dto.piezatalle;

import java.time.LocalDateTime;
import java.util.List;

/** Una fila existente de PiezaTalle. El frontend cruza esta lista contra la TablaTalle completa
 * del grupo de la Pieza para saber qué talles están "Pendiente" (sin fila acá). */
public record PiezaTalleResponse(
    Long idTalle,
    String talle,
    List<double[]> coordenadas,
    double areaCm2,
    double anchoCm,
    double largoCm,
    double perimetroCm,
    boolean esBase,
    boolean editadoManualmente,
    LocalDateTime fechaGeneracion
) {
}
