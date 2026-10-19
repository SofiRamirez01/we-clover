package com.weclover.backend.dto.corte;

/** Qué hay que cargar en el sistema para destrabar pendientes del corte. */
public enum TipoFaltanteCorte {
    /** Una moldería con posiciones de color sin pines (Molderías). */
    MOLDERIA_SIN_PINES,
    /** Una pieza sin geometría graduada para un talle que se necesita (Piezas). */
    PIEZA_SIN_TALLE_GRADUADO,
    /** Pines sin fila de insumo asignada en una ficha con varias filas de ese tipo (Ficha Técnica):
     *  uno por producto, con los pines en la descripción. */
    PIN_SIN_ASIGNAR,
    /** Un producto con posiciones de color sin marcar (Ficha Técnica). */
    PRODUCTO_SIN_COLORES
}
