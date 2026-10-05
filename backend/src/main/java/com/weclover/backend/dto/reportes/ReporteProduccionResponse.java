package com.weclover.backend.dto.reportes;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/reportes/produccion. Solo prendas habilitadas, no-Bandera, de pedidos vendidos (desde
 * SENADO, sin CANCELADO).
 *
 * porEstado es la foto ACTUAL (no depende de desde/hasta, solo de tipoPrenda): los 9 estados de
 * EstadoProduccion en orden de pipeline, incluidos los que tienen 0. unidadesEnPlanta es la suma
 * de todos menos ENTREGADO (lo que ya salió de planta no cuenta para ver el cuello de botella).
 *
 * terminadasPorMes sale de MovimientoEstado (entradas al estado TERMINADO) dentro de
 * [desde, hasta], con las unidades que tenía la prenda en ese momento.
 */
public record ReporteProduccionResponse(
    LocalDate desde,
    LocalDate hasta,
    Long idTipoPrenda,

    List<ProduccionPorEstadoResponse> porEstado,
    long unidadesEnPlanta,

    List<ProduccionPorMesResponse> terminadasPorMes
) {
}
