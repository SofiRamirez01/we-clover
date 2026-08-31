package com.weclover.backend.dto.planificacioncompra;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.weclover.backend.entity.EstadoPlanificacionCompra;

public record PlanificacionCompraResponse(
    Long id,
    String nombre,
    LocalDateTime fechaCreacion,
    LocalDate fechaDesde,
    LocalDate fechaHasta,
    Long idCreadoPor,
    String nombreCreadoPor,
    long cantidadProductos,
    EstadoPlanificacionCompra estado
) {
}
