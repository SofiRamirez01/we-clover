package com.weclover.backend.dto.tanda;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cambio de tanda de un pedido dentro de una sesión de priorización.
 *
 * `idTandaOrigenEsperada` es la tanda en la que el cliente vio al pedido al abrir el popup
 * (null = sin tanda): si ya no coincide con la real, otra persona lo movió mientras tanto y se
 * rechaza toda la sesión en vez de pisar ese cambio.
 */
public record MovimientoTandaRequest(

    @NotNull(message = "Debe indicar el pedido")
    Long idPedido,

    Long idTandaOrigenEsperada,

    /** Null = el pedido queda sin tanda. */
    @Valid
    TandaRefRequest destino,

    /** Obligatorio si el pedido sale de una tanda en la que ya estaba (ver
     *  PriorizacionTandaService). */
    @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
    String motivo
) {
}
