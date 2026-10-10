package com.weclover.backend.dto.tanda;

import java.util.List;

/** Resultado de una sesión de priorización: la sesión creada y la cola ya actualizada. */
public record PriorizacionResponse(
    Long idSesion,
    List<TandaResponse> tandas
) {
}
