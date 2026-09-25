package com.weclover.backend.dto.produccion;

import java.time.LocalDate;
import java.util.List;

import com.weclover.backend.entity.EstadoBandera;

/**
 * Si esBandera=false: estadoVisual/etapas están completos y estadoBandera/fechaPedidoProveedor/
 * fechaRecibido son null. Si esBandera=true: al revés — estadoVisual es null y etapas es una
 * lista vacía (Bandera no participa de ProductoEtapaProduccion, ver
 * EtapaProduccionAplicabilidad.esBandera).
 */
public record ProduccionProductoResponse(
    Long id,
    String tipoPrenda,
    int cantidadTotal,
    boolean esBandera,
    String estadoVisual,
    List<ProduccionEtapaResponse> etapas,
    EstadoBandera estadoBandera,
    LocalDate fechaPedidoProveedor,
    LocalDate fechaRecibido
) {
}
