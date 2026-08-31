package com.weclover.backend.dto.stock;

import java.time.LocalDateTime;

import com.weclover.backend.entity.UnidadMedida;

public record StockResponse(
    Long id,
    Long idPaletaColor,
    String nombreColor,
    String hexColor,
    Long idTipoTela,
    String codigoTipoTela,
    String nombreTipoTela,
    Long idProveedor,
    String nombreProveedor,
    float cantidad,
    UnidadMedida unidadMedida,
    LocalDateTime fechaUltimaActualizacion,
    Long idActualizadoPor,
    String nombreActualizadoPor
) {
}
