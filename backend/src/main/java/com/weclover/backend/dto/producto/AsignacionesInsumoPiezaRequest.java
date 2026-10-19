package com.weclover.backend.dto.producto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Set completo de asignaciones pin → fila de insumo de un producto (reemplaza al anterior). */
public record AsignacionesInsumoPiezaRequest(

    @NotNull(message = "Debe indicar la lista de asignaciones (puede ser vacía)")
    List<@Valid AsignacionInsumoPiezaItemRequest> asignaciones
) {
}
