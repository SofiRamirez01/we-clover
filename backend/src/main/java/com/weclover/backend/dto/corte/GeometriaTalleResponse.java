package com.weclover.backend.dto.corte;

import java.util.List;

/** Contorno de una pieza en un talle (PiezaTalle), en cm y sin margen de corte. */
public record GeometriaTalleResponse(
    Long idTalle,
    String talle,
    List<double[]> coordenadas,
    double anchoCm,
    double largoCm,
    double areaCm2
) {
}
