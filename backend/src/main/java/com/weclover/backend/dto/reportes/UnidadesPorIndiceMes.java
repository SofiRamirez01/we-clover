package com.weclover.backend.dto.reportes;

/**
 * Proyección agrupada por mes, donde indiceMes es la cantidad de meses completos transcurridos
 * desde el inicio del primer mes del período (0 = primer mes) — ver
 * ReporteProduccionRepository.terminadasPorMes para el porqué de no agrupar por year()/month().
 */
public record UnidadesPorIndiceMes(
    Integer indiceMes,
    Long unidades
) {
}
