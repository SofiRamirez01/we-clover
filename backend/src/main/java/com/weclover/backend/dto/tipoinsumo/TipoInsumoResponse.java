package com.weclover.backend.dto.tipoinsumo;

/**
 * Tipo de insumo secundario del catálogo. `soloTelasPorPeso` indica que las filas de este tipo
 * solo pueden usar telas que se compran por peso (son insumos que se cortan): vale para los
 * tipos cuya tela sugerida es por peso (Capucha, Puños y cintura).
 */
public record TipoInsumoResponse(
    Long id,
    String codigo,
    String nombre,
    boolean consumeMaterial,
    Long idTipoTelaSugerido,
    String codigoTipoTelaSugerido,
    boolean soloTelasPorPeso
) {
}
