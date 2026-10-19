package com.weclover.backend.dto.corte;

import java.util.List;

/**
 * Una parte de la demanda que no entra, con su motivo. Un mismo producto puede tener varias
 * entradas, una por motivo. `unidadesAfectadas` es la cantidadTotal del producto si el
 * pendiente es de todo el producto, o las unidades de ese talle / personalizadas si no.
 */
public record PendienteCorteResponse(
    MotivoPendienteCorte motivo,
    String detalle,
    Long idPedido,
    String codigoInternoPedido,
    Long idProducto,
    String tipoPrenda,
    int unidadesAfectadas,
    /** Solo para PIEZA_SIN_TALLE: el talle afectado. */
    Long idTalle,
    String talle,
    /** Pines sin resolver (motivos de ResolucionTelaPin) o piezas sin geometría (PIEZA_SIN_TALLE). */
    List<PinPendienteResponse> pines
) {
}
