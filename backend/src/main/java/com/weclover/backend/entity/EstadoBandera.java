package com.weclover.backend.entity;

/**
 * Flujo propio de un Producto Bandera (se compra a un proveedor externo, no pasa por el taller
 * — ver Producto.estadoBandera). No bloquea TERMINADO del pedido, pero sí ENTREGADO (ver
 * PedidoService, validación al marcar un pedido como entregado).
 */
public enum EstadoBandera {
    PENDIENTE,
    PEDIDO,
    RECIBIDO
}
