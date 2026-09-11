package com.weclover.backend.dto.pieza;

import java.util.List;

/**
 * Versión liviana de una Pieza para el picker (buscador de Piezas al asignarlas a una posición
 * del patrón de corte) y para embeber dentro de PatronCortePosicionPiezaResponse: trae la
 * geometría de su talle base (para la miniatura) en vez de todos los datos administrativos de
 * PiezaResponse (grupo, talle base como texto, activo).
 */
public record PiezaResumenResponse(
    Long id,
    String nombre,
    boolean simetrica,
    List<double[]> coordenadas,
    double anchoCm,
    double largoCm,
    /** true si ya se resolvió (generó o corrigió) el contorno de todos los talles del grupo de esta Pieza. */
    boolean graduacionCompleta
) {
}
