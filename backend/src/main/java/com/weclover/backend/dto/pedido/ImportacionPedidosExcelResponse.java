package com.weclover.backend.dto.pedido;

import java.util.List;

public record ImportacionPedidosExcelResponse(
    int totalFilas,
    int importados,
    List<PedidoImportadoResumen> pedidosImportados,
    List<FilaImportacionSaltada> filasSalteadas
) {
}
