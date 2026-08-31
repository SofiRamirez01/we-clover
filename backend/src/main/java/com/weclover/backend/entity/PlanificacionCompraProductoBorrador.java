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
 * Un producto tildado dentro de una PlanificacionCompra todavía en BORRADOR — a diferencia de
 * PlanificacionCompraDetalle, no calcula nada (cantidad/tipoTela/color): un producto puede
 * estar acá sin tener el diseño completo, justamente porque el usuario todavía está decidiendo.
 * El cálculo recién ocurre al confirmar (ver PlanificacionCompraService.confirmar), momento en
 * el que estas filas se vacían y se reemplazan por PlanificacionCompraDetalle.
 */
@Entity
@Table(name = "planificacion_compra_productos_borrador",
    uniqueConstraints = @UniqueConstraint(name = "uk_planificacion_borrador_producto", columnNames = { "id_planificacion_compra", "id_producto" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class PlanificacionCompraProductoBorrador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_planificacion_compra", nullable = false)
    private PlanificacionCompra planificacionCompra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;
}
