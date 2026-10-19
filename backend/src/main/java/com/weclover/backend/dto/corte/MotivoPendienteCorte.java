package com.weclover.backend.dto.corte;

/**
 * Por qué una parte de la demanda no entra en la preparación del corte. Los cinco últimos son
 * los motivos de ResolucionTelaPin para un pin: cualquiera de ellos deja pendiente todo el
 * producto, para no cortar una prenda a medias.
 */
public enum MotivoPendienteCorte {
    /** El producto no tiene tela asignada. Todo el producto. */
    SIN_TELA,
    /** El producto no tiene moldería. Todo el producto. */
    SIN_MOLDERIA,
    /** Hay posiciones de color de la moldería sin color marcado. Todo el producto. */
    COLORES_INCOMPLETOS,
    /** Faltan unidades por asignar o por medir en la carga de talles. Todo el producto. */
    TALLES_INCOMPLETOS,
    /** Ninguna fila de la tabla cubre la medida: no se escala la moldería. Solo esas unidades. */
    TALLE_PERSONALIZADO,
    /** Una pieza no tiene geometría (PiezaTalle) para un talle. Todas las unidades de ese talle. */
    PIEZA_SIN_TALLE,
    /** Una posición de color de la moldería no tiene ningún pin. Todo el producto. */
    POSICION_SIN_PINES,
    FALTA_INSUMO,
    SIN_ASIGNAR,
    INSUMO_INCOMPLETO,
    TELA_NO_CORTABLE,
    CUERPO_INCOMPLETO
}
