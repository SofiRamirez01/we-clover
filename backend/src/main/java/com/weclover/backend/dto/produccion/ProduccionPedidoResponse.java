package com.weclover.backend.dto.produccion;

import java.time.LocalDate;
import java.util.List;

import com.weclover.backend.dto.pedido.NotaPedidoResponse;
import com.weclover.backend.entity.EstadoPedido;

public record ProduccionPedidoResponse(
    Long id,
    String codigoInterno,
    String colegio,
    String curso,
    EstadoPedido estadoActual,
    LocalDate fechaVenta,
    LocalDate fechaEstimadaEntrega,
    float porcentajePagado,
    /** Puntaje sugerido: rank por % de pago entre los pedidos activos. Solo ordena los pedidos
     *  sin tanda y sirve de referencia; nunca asigna ni reordena tandas. Null si el pedido no
     *  está activo (ENTREGADO/CANCELADO) — ver EstadoPedidoService. */
    Integer prioridadAutomatica,
    /** Null = sin tanda. */
    ProduccionTandaResponse tanda,
    String ubicacionActual,
    /** La nota más reciente del pedido (null si no tiene); el resto se pide aparte. */
    NotaPedidoResponse ultimaNota,
    int cantidadNotas,
    List<ProduccionProductoResponse> productos
) {
}
