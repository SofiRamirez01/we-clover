package com.weclover.backend.dto.pedido;

/** DTO propio (y no PedidoResponse) porque la ubicación es información interna. */
public record UbicacionPedidoResponse(
    Long idPedido,
    String ubicacionActual
) {
}
