package com.weclover.backend.dto.patroncorte;

import java.util.List;

import com.weclover.backend.dto.tipoprenda.TipoPrendaResponse;

public record PatronCorteResponse(
    Long id,
    Integer numeroInterno,
    String nombre,
    List<TipoPrendaResponse> tiposPrenda,
    String imagenUrl,
    int cantidadColores,
    boolean activo,
    List<PatronCorteColorResponse> colores,
    /** Derivado de tiposPrenda (ver PatronCorteGrupoTalleResolver); null si no hay un único
     * grupo de talle determinable — en ese caso no se le pueden asignar Piezas a los pines. */
    Long idGrupoTalle,
    String nombreGrupoTalle
) {
}
