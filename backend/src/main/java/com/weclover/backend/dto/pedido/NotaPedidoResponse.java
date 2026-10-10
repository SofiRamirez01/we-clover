package com.weclover.backend.dto.pedido;

import java.time.LocalDateTime;

/** Nota interna de un pedido (ver NotaPedido) — nunca se expone a ROLE_CLIENTE. */
public record NotaPedidoResponse(
    Long id,
    String texto,
    String nombreAutor,
    LocalDateTime fecha
) {
}
