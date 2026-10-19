package com.weclover.backend.dto.corte;

/**
 * Algo que falta cargar y las unidades que destrabaría (sumadas entre todos los productos que
 * dependen de eso). `idReferencia` es el id de la moldería (MOLDERIA_SIN_PINES), de la pieza
 * (PIEZA_SIN_TALLE_GRADUADO) o del producto cuya ficha hay que completar (PIN_SIN_ASIGNAR,
 * PRODUCTO_SIN_COLORES). `descripcion` dice dónde se carga y qué falta.
 */
public record FaltanteCargaCorteResponse(
    TipoFaltanteCorte tipo,
    Long idReferencia,
    String descripcion,
    int unidadesAfectadas
) {
}
