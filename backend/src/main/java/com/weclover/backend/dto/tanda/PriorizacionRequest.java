package com.weclover.backend.dto.tanda;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Todo lo que el popup de priorización cambió de una vez (POST /api/tandas/priorizacion): se
 * aplica completo o no se aplica nada.
 */
public record PriorizacionRequest(

    @Size(max = 500, message = "La nota no puede superar los 500 caracteres")
    String nota,

    @Valid
    List<TandaNuevaRequest> tandasNuevas,

    /** Orden final de la cola: todas las tandas no cerradas más las nuevas, cada una una sola
     *  vez. Null = no se reordena (las nuevas entran en su lugar alfabético). */
    @Valid
    List<TandaRefRequest> ordenTandas,

    @Valid
    List<MovimientoTandaRequest> movimientos
) {
}
