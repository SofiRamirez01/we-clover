package com.weclover.backend.entity;

/**
 * Categoriza cada fila de PaletaColores: FRIZA/JERSEY/PIQUE son telas reales de la prenda,
 * CIERRE agrupa los colores disponibles para el cierre de una campera (no es una tela, pero
 * se unifica en el mismo catálogo/enum a pedido del negocio). Producto.tipoTela solo admite
 * los tres primeros valores (ver ProductoService.actualizarTipoTela).
 */
public enum TipoTela {
    FRIZA,
    JERSEY,
    PIQUE,
    SPUM,
    CIERRE
}
