package com.weclover.backend.dto.producto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Reemplaza el set completo de insumos secundarios del producto (borra e inserta), mismo
 * criterio que ProductoColoresRequest para los colores del patrón. A diferencia de ese, acá
 * la lista puede ser vacía: un producto puede legítimamente no tener ningún insumo
 * secundario (ej. una Chomba sin cierre, capucha ni puños).
 */
public record ProductoInsumosSecundariosRequest(

    @NotNull(message = "Debe indicar la lista de insumos (puede ser vacía)")
    List<@Valid ProductoInsumoSecundarioItemRequest> insumos
) {
}
