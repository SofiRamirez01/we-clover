package com.weclover.backend.dto.corte;

import java.util.List;

/**
 * Demanda de piezas de una tanda, lista para el algoritmo de corte (Módulo 4): contrato de
 * entrada del paso siguiente. Es solo lectura: no guarda nada.
 */
public record PreparacionCorteResponse(
    Long idTanda,
    String nombreTanda,
    ParametrosCorteResponse parametros,
    List<PiezaCorteResponse> piezas,
    List<GrupoTelaCorteResponse> grupos,
    List<PendienteCorteResponse> pendientes,
    ResumenCorteResponse resumen
) {
}
