package com.weclover.backend.dto.pieza;

import java.util.List;

/**
 * Espejo del CalcularBaseResponse que devuelve services/pieza-geometria. Calculado directamente
 * sobre el polígono base tal cual lo cargó el usuario (sin ningún buffer/margen): el margen de
 * costura ya está físicamente incluido en las coordenadas medidas de la moldería, y el margen
 * de navegación de corte (create_margin_polygons) es harina de otro costal — pertenece
 * exclusivamente a la fase futura de optimización de layout, todavía no integrada.
 */
public record PiezaGeometriaCalculoResponse(
    List<double[]> coordenadas,
    double areaCm2,
    double anchoCm,
    double largoCm,
    double perimetroCm
) {
}
