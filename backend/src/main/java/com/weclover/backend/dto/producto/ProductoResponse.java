package com.weclover.backend.dto.producto;

import java.util.List;

import com.weclover.backend.dto.patroncorte.PatronCorteColorResponse;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.TipoTela;

public record ProductoResponse(
    Long id,
    Long idTipoPrenda,
    String tipoPrenda,
    Long idPatronCorte,
    List<PatronCorteColorResponse> patronCorteColores,
    TipoTela tipoTela,
    Long idColorCierre,
    String nombreColorCierre,
    String hexColorCierre,
    int cantidadTotal,
    float costo,
    float subtotal,
    String observaciones,
    String imagenDisenoUrl,
    EstadoPedido estadoActual,
    List<ProductoColorResponse> colores
) {
}
