package com.weclover.backend.dto.tanda;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tanda con sus datos derivados. `id` es la única clave: `nombre` es una etiqueta editable y
 * `posicion` cambia sola cuando se cierra una tanda anterior.
 */
public record TandaResponse(
    Long id,
    String nombre,
    EstadoTanda estado,
    /** Lugar en la cola ("tanda 1, 2, 3…") contando solo las tandas no cerradas. Null si la
     *  tanda está CERRADA. */
    Integer posicion,
    int cantidadPedidos,
    List<UnidadesPorTipoPrendaResponse> unidadesPorTipoPrenda,
    LocalDateTime fechaCreacion,
    String nombreCreador
) {
}
