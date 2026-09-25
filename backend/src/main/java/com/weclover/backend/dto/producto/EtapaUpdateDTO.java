package com.weclover.backend.dto.producto;

import com.weclover.backend.entity.EtapaProduccion;

import jakarta.validation.constraints.NotNull;

/** Una fila de la carga semanal masiva (ver ProductoController, PUT /productos/etapas/bulk). */
public record EtapaUpdateDTO(

    @NotNull(message = "Debe indicar el producto")
    Long idProducto,

    @NotNull(message = "Debe indicar la etapa")
    EtapaProduccion etapa,

    @NotNull(message = "Debe indicar si la etapa se completa o se revierte")
    Boolean completado,

    Long idEmpleado
) {
}
