package com.weclover.backend.dto.pedido;

/**
 * Columnas por las que se puede ordenar el listado de Base Ventas (ver
 * PedidoListadoService / OrdenListadoPedidos). Sin campo elegido, el listado va del pedido más
 * nuevo al más viejo (fecha de creación).
 */
public enum CampoOrdenPedidos {
    /** Por promoción y número (2026-99 antes que 2026-100). */
    FICHA,
    FECHA_VENTA,
    FECHA_ENTREGA,
    COLEGIO,
    LOCALIDAD,
    CURSO,
    VENDEDOR,
    /** Unidades de Buzo + Campera. */
    UNIDADES_BUZO_CAMPERA,
    UNIDADES_REMERA,
    UNIDADES_CHOMBA,
    PRECIO_UNITARIO,
    TOTAL,
    PORCENTAJE_PAGO,
    /** Orden del ciclo de vida (Presupuestado → … → Entregado, Cancelado al final), no alfabético. */
    ESTADO
}
