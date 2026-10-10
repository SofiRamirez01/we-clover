package com.weclover.backend.dto.tanda;

/**
 * Referencia a una tanda dentro de una sesión de priorización: `id` si ya existe, o
 * `idTemporal` si se crea en esta misma sesión (ver TandaNuevaRequest). Exactamente uno de los
 * dos. Nunca se referencia una tanda por nombre.
 */
public record TandaRefRequest(
    Long id,
    String idTemporal
) {
}
