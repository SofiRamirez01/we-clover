package com.weclover.backend.entity;

/** Quién coordina la carga de medidas/talles del curso: el propio alumno o un adulto
 *  (padre/madre, docente, etc.). Dato informativo del Pedido, no condiciona ningún flujo hoy. */
public enum ResponsableCurso {
    ALUMNO,
    ADULTO
}
