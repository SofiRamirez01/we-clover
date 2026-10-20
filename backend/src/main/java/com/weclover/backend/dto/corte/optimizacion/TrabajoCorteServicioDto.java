package com.weclover.backend.dto.corte.optimizacion;

import tools.jackson.databind.JsonNode;

/** Respuesta del servicio de cortes (POST y GET /corte/trabajos). Los instantes vienen en segundos epoch. */
public record TrabajoCorteServicioDto(
    String idTrabajo,
    Long idTanda,
    String estado,
    Integer posicionEnCola,
    ProgresoCorteResponse progreso,
    Double creado,
    Double iniciado,
    Double terminado,
    Double venceEn,
    String error,
    JsonNode resultado
) {
}
