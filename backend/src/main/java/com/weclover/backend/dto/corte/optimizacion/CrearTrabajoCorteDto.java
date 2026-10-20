package com.weclover.backend.dto.corte.optimizacion;

import java.util.Map;

import com.weclover.backend.dto.corte.PreparacionCorteResponse;

/** Cuerpo saliente hacia POST /corte/trabajos del servicio de cortes. */
public record CrearTrabajoCorteDto(
    PreparacionCorteResponse preparacion,
    Map<String, Object> parametros
) {
}
