package com.weclover.backend.dto.planificacioncompra;

import com.weclover.backend.entity.UnidadMedida;

/** Fila sin agrupar de una PlanificacionCompra, para la vista de detalle por producto/pedido. */
public record PlanificacionCompraDetalleResponse(
    Long id,
    Long idProducto,
    String tipoPrenda,
    Long idPedido,
    String codigoInternoPedido,
    String nombreColegio,
    Long idTipoTela,
    String tipoTela,
    String nombreTipoTela,
    Long idPaletaColor,
    String nombreColor,
    String hexColor,
    float cantidad,
    UnidadMedida unidadMedida
) {
}
