package com.weclover.backend.dto.cargatalles;

import java.util.List;

/** Tabla de talles de referencia de un grupo (ej. "Chomba/Remera"), ordenada de menor a mayor
 *  — para mostrarla en la pantalla pública como guía de qué talle corresponde a qué medida. */
public record GrupoTallaResponse(
    String nombreGrupo,
    List<FilaTablaTalleResponse> filas
) {
}
