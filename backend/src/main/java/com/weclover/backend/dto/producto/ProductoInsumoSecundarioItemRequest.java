package com.weclover.backend.dto.producto;

import jakarta.validation.constraints.Size;

/**
 * Una fila de insumo secundario de la ficha. Qué campos son obligatorios depende del tipo de
 * insumo, así que se valida en ProductoService.actualizarInsumosSecundarios y no acá:
 * - tipo con material (Capucha, Puños y cintura, Cierre) o insumo libre: tela, color y
 *   cantidad mayor a cero;
 * - tipo sin material (Estampado): no lleva tela, color ni cantidad.
 */
public record ProductoInsumoSecundarioItemRequest(

    /** Id de la fila ya guardada que se está editando, o null si es una fila nueva. Las filas
     *  del producto que no vengan en la lista se borran (con sus asignaciones de pines). */
    Long id,

    /** Tipo del catálogo (ver TipoInsumo). Null = insumo libre, identificado por `descripcion`. */
    Long idTipoInsumo,

    /** Texto visible de la fila. Obligatorio para un insumo libre; para una fila con tipo
     *  puede venir vacío y se completa con el nombre del tipo. */
    @Size(max = 100, message = "La descripción no puede superar los 100 caracteres")
    String descripcion,

    Long idTipoTela,

    Long idPaletaColor,

    Float cantidad,

    /**
     * true si el usuario escribió la cantidad a mano; false si es la sugerida (en ese caso el
     * backend la recalcula con el reparto, ver RepartoGramosInsumo). Si no viene, una fila
     * existente conserva su indicador salvo que la cantidad haya cambiado (pasa a true), y una
     * fila nueva se toma como no editada.
     */
    Boolean cantidadEditadaManualmente
) {
}
