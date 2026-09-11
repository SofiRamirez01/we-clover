package com.weclover.backend.dto.pedido;

public record PedidoImportadoResumen(
    int filaExcel,
    Long idPedido,
    String codigoInterno,
    String colegio
) {
}
