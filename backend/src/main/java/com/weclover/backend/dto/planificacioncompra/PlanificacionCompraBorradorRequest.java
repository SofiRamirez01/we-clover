package com.weclover.backend.dto.planificacioncompra;

import java.util.List;

import jakarta.validation.constraints.Size;

/**
 * A diferencia de PlanificacionCompraRequest (que ya no existe: se reemplazó por este + el
 * endpoint de confirmar), acá nada es obligatorio — un borrador puede guardarse a mitad de
 * completar. Las validaciones de "obligatorio" se hacen recién en
 * PlanificacionCompraService.confirmar.
 *
 * El período (fechaDesde/fechaHasta) ya no viaja: lo calcula el servicio a partir de las fechas
 * de entrega de los productos tildados.
 */
public record PlanificacionCompraBorradorRequest(

    @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
    String nombre,

    List<Long> idsProductos
) {
}
