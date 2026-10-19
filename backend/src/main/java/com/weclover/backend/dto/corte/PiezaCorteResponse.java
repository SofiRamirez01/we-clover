package com.weclover.backend.dto.corte;

import java.util.List;

/**
 * Una pieza que aparece en la demanda, con su geometría solo para los talles que se usan. Los
 * ítems de `grupos` la referencian por id, así la geometría no se repite por cada ítem.
 * `simetrica` no cambia ninguna cantidad: viaja como dato para el algoritmo.
 */
public record PiezaCorteResponse(
    Long id,
    String nombre,
    boolean simetrica,
    List<GeometriaTalleResponse> talles
) {
}
