package com.weclover.backend.dto.tanda;

/**
 * Estado de una Tanda. Es derivado de sus pedidos (ver TandaEstadoCalculador), nunca se
 * persiste ni se setea a mano:
 * - PLANIFICADA: ningún pedido tiene una etapa completada (incluye la tanda vacía).
 * - EN_PRODUCCION: algún pedido tiene al menos una etapa completada.
 * - CERRADA: tiene pedidos y todos están ENTREGADO o CANCELADO.
 */
public enum EstadoTanda {
    PLANIFICADA,
    EN_PRODUCCION,
    CERRADA
}
