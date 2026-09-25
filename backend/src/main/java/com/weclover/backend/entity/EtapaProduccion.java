package com.weclover.backend.entity;

/**
 * Etapas de producción de un Producto no-Bandera (ver ProductoEtapaProduccion). El orden de
 * declaración es el orden del pipeline — se usa para determinar la "última etapa completada"
 * (ver ProductoEtapaProduccionService, estado visible 3.3). No hay validación de orden
 * secuencial entre etapas: se pueden marcar en cualquier orden.
 */
public enum EtapaProduccion {
    CORTE,
    ESTAMPADO,
    BORDADO,
    CONFECCION,
    APODO,
    OJAL,
    CONTROL
}
