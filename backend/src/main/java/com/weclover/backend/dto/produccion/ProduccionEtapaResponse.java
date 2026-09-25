package com.weclover.backend.dto.produccion;

import java.time.LocalDate;

import com.weclover.backend.entity.EtapaProduccion;

/** Siempre incluye las 7 etapas, con aplica=false para las que no correspondan a ese producto
 *  — evita que el frontend tenga que recalcular aplicabilidad (ver EtapaProduccionAplicabilidad
 *  en el backend). */
public record ProduccionEtapaResponse(
    EtapaProduccion etapa,
    boolean aplica,
    boolean completado,
    LocalDate fechaCompletado,
    Long empleadoId,
    String empleadoNombre
) {
}
