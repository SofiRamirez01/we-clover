package com.weclover.backend.dto.produccion;

import com.weclover.backend.dto.tanda.EstadoTanda;

/** Tanda de un pedido en la grilla de Producción. `id` es la clave; `nombre` es solo etiqueta. */
public record ProduccionTandaResponse(
    Long id,
    String nombre,
    /** Lugar en la cola entre las tandas no cerradas; null si la tanda está CERRADA. */
    Integer posicion,
    EstadoTanda estado
) {
}
