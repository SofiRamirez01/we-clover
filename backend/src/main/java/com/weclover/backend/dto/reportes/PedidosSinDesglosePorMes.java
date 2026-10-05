package com.weclover.backend.dto.reportes;

/**
 * Proyección por mes de los pedidos "sin desglose": importados de Excel con más de un tipo de
 * prenda, donde Producto.costo es 0 y el total real está en Pedido.montoReferenciaImportado.
 */
public record PedidosSinDesglosePorMes(
    Integer anio,
    Integer mes,
    Long pedidos,
    Double montoReferencia
) {
}
