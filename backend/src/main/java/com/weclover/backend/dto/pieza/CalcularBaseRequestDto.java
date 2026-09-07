package com.weclover.backend.dto.pieza;

import java.util.List;

/**
 * Cuerpo saliente hacia POST /piezas/calcular-base del servicio de geometría. `simetrica` viaja
 * en el contrato para dejarlo cerrado de cara a la fase de optimización de layout (todavía no
 * se usa para ningún cálculo: área/ancho/largo/perímetro no dependen de esto).
 */
public record CalcularBaseRequestDto(
    List<SegmentoDto> segmentos,
    boolean simetrica
) {
}
