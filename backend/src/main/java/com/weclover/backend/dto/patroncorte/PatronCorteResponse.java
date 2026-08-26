package com.weclover.backend.dto.patroncorte;

import java.util.List;

public record PatronCorteResponse(
    Long id,
    Integer numeroInterno,
    String nombre,
    Long idTipoPrenda,
    String tipoPrenda,
    String imagenUrl,
    int cantidadColores,
    boolean activo,
    List<PatronCorteColorResponse> colores
) {
}
