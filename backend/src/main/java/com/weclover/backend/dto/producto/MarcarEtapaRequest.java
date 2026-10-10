package com.weclover.backend.dto.producto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MarcarEtapaRequest(

    @NotNull(message = "Debe indicar si la etapa se completa o se revierte")
    Boolean completado,

    Long idEmpleado,

    /** Opcional: aclaración libre del marcado/desmarcado (ej. "se repite bordado por falla").
     *  Queda en MovimientoEstado.observaciones y se ve en el historial del pedido. */
    @Size(max = 255, message = "El comentario no puede superar los 255 caracteres")
    String comentario
) {
}
