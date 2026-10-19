package com.weclover.backend.dto.producto;

public record ProductoInsumoSecundarioResponse(
    Long id,
    String descripcion,
    /** Todo lo siguiente es null para una fila-flag pura (ej. descripcion="Estampado" sin
     *  tela/color/cantidad reales — ver ProductoInsumoSecundario). */
    Long idTipoTela,
    String tipoTela,
    String nombreTipoTela,
    Boolean esPorPeso,
    Long idPaletaColor,
    String nombreColor,
    String hexColor,
    Float cantidad,
    /** Tipo del catálogo (ver TipoInsumo); los tres en null para un insumo libre. */
    Long idTipoInsumo,
    String codigoTipoInsumo,
    String nombreTipoInsumo,
    /** true si `cantidad` fue escrita a mano; false si es la sugerida por el reparto. */
    boolean cantidadEditadaManualmente
) {
}
