package com.weclover.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Qué fila de insumo de la ficha (productoInsumo) cubre un pin concreto de la moldería del
 * producto (posicionPieza). Solo hace falta cuando el producto tiene dos o más filas del mismo
 * tipo de insumo (ej. dos colores de Puños y cintura): con una sola fila, esa fila vale para
 * todos los pines del tipo y no se guarda nada acá (ver AsignacionInsumoPieza).
 *
 * `producto` repite al dueño de productoInsumo a propósito: es lo que permite la restricción de
 * unicidad (un pin tiene, como mucho, una fila asignada dentro de un producto).
 *
 * Es hija de ProductoInsumoSecundario (cascada + orphanRemoval): se borra junto con su fila.
 * También se descarta cuando cambia la moldería del producto (ProductoService) y cuando el pin
 * se borra o pasa a otra pieza de otro tipo (PatronCortePosicionPiezaService).
 */
@Entity
@Table(name = "producto_insumo_pieza",
    uniqueConstraints = @UniqueConstraint(name = "uk_producto_insumo_pieza_producto_posicion",
        columnNames = { "id_producto", "id_posicion_pieza" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ProductoInsumoPieza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_producto_insumo", nullable = false)
    private ProductoInsumoSecundario productoInsumo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_posicion_pieza", nullable = false)
    private PatronCortePosicionPieza posicionPieza;
}
