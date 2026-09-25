package com.weclover.backend.dto.producto;

import java.time.LocalDate;

import com.weclover.backend.entity.EtapaProduccion;

public record EtapaProduccionResponse(
    EtapaProduccion etapa,
    boolean completado,
    LocalDate fechaCompletado,
    Long idEmpleado,
    String nombreEmpleado
) {
}
