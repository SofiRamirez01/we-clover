package com.weclover.backend.dto.producto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProductoInsumoSecundarioItemRequest(

    /**
     * Identifica de qué insumo se trata (ej. "Capucha", "Puños y cintura", "Cierre", o texto
     * libre para uno agregado a mano) — es la clave real de la fila, no idTipoTela (ver
     * ProductoInsumoSecundario). Debe ser única dentro de la lista y dentro del producto.
     */
    @NotBlank(message = "Debe indicar para qué es este insumo")
    @Size(max = 100, message = "La descripción no puede superar los 100 caracteres")
    String descripcion,

    @NotNull(message = "Debe indicar el tipo de tela del insumo")
    Long idTipoTela,

    @NotNull(message = "Debe seleccionar el color del insumo")
    Long idPaletaColor,

    @Positive(message = "La cantidad debe ser mayor a cero")
    float cantidad
) {
}
