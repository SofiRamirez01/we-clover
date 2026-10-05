package com.weclover.backend.entity;

/**
 * Estado de producción persistido de un Producto no-Bandera (Producto.estadoProduccion), en
 * orden de pipeline. Se deriva de sus etapas (ver EstadoProduccionCalculador) y lo mantiene
 * actualizado ProductoEtapaProduccionService.recalcularEstadoProduccion — nunca se setea a mano.
 *
 * No hay estado CONTROL: marcar CONTROL (con el resto de las etapas aplicables completas) es lo
 * que lleva el producto a TERMINADO. OJAL solo es alcanzable por una Chomba (ver
 * EtapaProduccionAplicabilidad). TERMINADO = listo pero todavía en planta; ENTREGADO = su pedido
 * se marcó como ENTREGADO (ya salió de planta).
 */
public enum EstadoProduccion {
    PENDIENTE,
    CORTADO,
    ESTAMPADO,
    BORDADO,
    CONFECCION,
    APODO,
    OJAL,
    TERMINADO,
    ENTREGADO
}
