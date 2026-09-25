package com.weclover.backend.dto.producto;

import java.util.List;

/** estadoVisual: ver 3.3 de la entrega de Producción — null si el producto es Bandera (usa
 *  Producto.estadoBandera en su lugar, no esta tabla). */
public record ProductoEtapasResponse(
    Long idProducto,
    List<EtapaProduccionResponse> etapas,
    String estadoVisual
) {
}
