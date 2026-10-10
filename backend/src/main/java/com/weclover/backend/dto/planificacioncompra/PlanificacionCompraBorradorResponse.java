package com.weclover.backend.dto.planificacioncompra;

import java.util.List;

/** Para "continuar editando" un borrador desde el listado: trae los ids ya tildados. */
public record PlanificacionCompraBorradorResponse(
    Long id,
    String nombre,
    List<Long> idsProductos
) {
}
