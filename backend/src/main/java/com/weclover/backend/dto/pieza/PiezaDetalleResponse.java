package com.weclover.backend.dto.pieza;

import java.util.List;

/**
 * Detalle completo de una Pieza, incluyendo los segmentos originales (para reabrir el editor al
 * editar o duplicar) — a diferencia de PiezaResponse, pensado liviano para el listado.
 */
public record PiezaDetalleResponse(
    Long id,
    String nombre,
    Long idGrupoTalle,
    String nombreGrupoTalle,
    Long idTalleBase,
    String talleBase,
    List<SegmentoDto> segmentos,
    boolean simetrica,
    double anchoBaseCm,
    double largoBaseCm,
    boolean activo
) {
}
