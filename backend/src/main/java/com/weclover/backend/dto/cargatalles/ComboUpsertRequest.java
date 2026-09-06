package com.weclover.backend.dto.cargatalles;

import jakarta.validation.constraints.Positive;

/**
 * Ancho/largo nulos: guarda la unidad tal cual (recién agregada, todavía sin medir) sin tocar
 * el cálculo de talle. Cuando ambos vienen cargados, dispara el recálculo (ver
 * CargaTallesService.calcularTalle). `observacionPersonalizado` se guarda igual sin importar el
 * resultado del cálculo — no tiene sentido salvo que haya quedado personalizado, pero no hace
 * falta forzar esa regla en el DTO.
 */
public record ComboUpsertRequest(

    @Positive(message = "El ancho debe ser mayor a cero")
    Integer anchoCm,

    @Positive(message = "El largo debe ser mayor a cero")
    Integer largoCm,

    String observacionPersonalizado
) {
}
