package com.weclover.backend.dto.producto;

import java.time.LocalDate;
import java.util.List;

import com.weclover.backend.dto.patroncorte.PatronCorteColorResponse;
import com.weclover.backend.entity.EstadoBandera;

public record ProductoResponse(
    Long id,
    Long idTipoPrenda,
    String tipoPrenda,
    Long idPatronCorte,
    List<PatronCorteColorResponse> patronCorteColores,
    String tipoTela,
    Long idColorCierre,
    String nombreColorCierre,
    String hexColorCierre,
    int cantidadTotal,
    float costo,
    float subtotal,
    String observaciones,
    String imagenDisenoUrl,
    /** Solo se usan si tipoPrenda es Bandera (ver EstadoBandera) — null para el resto. */
    EstadoBandera estadoBandera,
    LocalDate fechaPedidoProveedor,
    LocalDate fechaRecibido,
    List<ProductoColorResponse> colores,
    List<ProductoInsumoSecundarioResponse> insumosSecundarios
) {
}
