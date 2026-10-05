package com.weclover.backend.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.reportes.ReporteProduccionResponse;
import com.weclover.backend.dto.reportes.ReporteVentasResponse;
import com.weclover.backend.service.ReporteService;

import lombok.RequiredArgsConstructor;

/**
 * Módulo 5 — Reportes (solo lectura, solo ROLE_ADMINISTRATIVO). Filtros comunes opcionales:
 * desde/hasta (YYYY-MM-DD; por defecto los últimos 12 meses) y tipoPrenda (id de TipoPrenda; sin
 * valor = todas).
 */
@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteService reporteService;

    @GetMapping("/ventas")
    public ReporteVentasResponse ventas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "tipoPrenda", required = false) Long idTipoPrenda,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return reporteService.ventas(desde, hasta, idTipoPrenda, idUsuarioActor);
    }

    /** porEstado es la foto actual (solo la afecta tipoPrenda); desde/hasta acotan terminadasPorMes. */
    @GetMapping("/produccion")
    public ReporteProduccionResponse produccion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "tipoPrenda", required = false) Long idTipoPrenda,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return reporteService.produccion(desde, hasta, idTipoPrenda, idUsuarioActor);
    }
}
