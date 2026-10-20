package com.weclover.backend.dto.corte.optimizacion;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

/**
 * Estado de un trabajo de optimización: EN_COLA, EJECUTANDO, TERMINADO, ERROR o CANCELADO.
 * `resultado` (solo si TERMINADO) va tal cual lo arma el servicio de cortes (por corte: tela,
 * largo, eficiencia, capas por color, piezas ubicadas con su polígono en cm, producción frente a
 * demanda; más noResueltas, advertencias y resumen); todavía no se persiste. `error`, solo si ERROR.
 */
public record TrabajoCorteResponse(
    String idTrabajo,
    Long idTanda,
    String estado,
    Integer posicionEnCola,
    ProgresoCorteResponse progreso,
    Instant creado,
    Instant iniciado,
    Instant terminado,
    Instant venceEn,
    String error,
    JsonNode resultado
) {
}
