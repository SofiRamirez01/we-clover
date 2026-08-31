package com.weclover.backend.dto.planificacioncompra;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Size;

/**
 * A diferencia de PlanificacionCompraRequest (que ya no existe: se reemplazó por este + el
 * endpoint de confirmar), acá nada es obligatorio — un borrador puede guardarse a mitad de
 * completar. Las validaciones de "obligatorio" se hacen recién en
 * PlanificacionCompraService.confirmar.
 */
public record PlanificacionCompraBorradorRequest(

    @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
    String nombre,

    LocalDate fechaDesde,

    LocalDate fechaHasta,

    List<Long> idsProductos
) {
}
