package com.weclover.backend.dto.piezatalle;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/** Upsert en una sola transacción de varios talles no-base a la vez: se usa al recalcular en
 * cadena todos los talles automáticos (editadoManualmente=false) después de editar la base. */
public record PiezaTalleBatchRequest(
    @NotEmpty @Valid List<PiezaTalleBatchItemRequest> talles
) {
}
