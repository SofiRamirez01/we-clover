package com.weclover.backend.dto.reportes;

import java.time.LocalDate;

/**
 * Filtros comunes de los reportes, ya normalizados (ver ReporteService.resolverFiltros): desde y
 * hasta nunca son null acá; idTipoPrenda null = todas las prendas. Es un objeto y no parámetros
 * sueltos para poder sumar después "empleado" y una agrupación semanal sin cambiar las firmas.
 */
public record ReporteFiltros(
    LocalDate desde,
    LocalDate hasta,
    Long idTipoPrenda
) {
}
