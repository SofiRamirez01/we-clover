package com.weclover.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una UNIDAD de un Producto puntual del pedido asignada a un alumno (el "combo") — con su
 * propia medida. Un mismo alumno puede tener varias filas para el mismo Producto (ej. 2
 * remeras), cada una con su propio ancho/largo/talle, porque nada garantiza que dos unidades
 * del mismo alumno midan igual — por eso NO hay unique constraint sobre
 * (id_alumno_pedido, id_producto): esa combinación se repite tantas veces como unidades se
 * hayan agregado. Se crea al agregar una unidad para ese alumno (ancho/largo todavía null) y se
 * completa/edita después con la medida. `talleAsignado` se recalcula automáticamente cada vez
 * que se cargan ancho/largo (ver CargaTallesService.calcularTalle) — null +
 * `personalizado = true` cuando ninguna fila de la tabla cubre ambas medidas a la vez.
 */
@Entity
@Table(name = "alumno_producto_talles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class AlumnoProductoTalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_alumno_pedido", nullable = false)
    private AlumnoPedido alumnoPedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @Column(name = "ancho_cm")
    private Integer anchoCm;

    @Column(name = "largo_cm")
    private Integer largoCm;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_tabla_talle")
    private TablaTalle talleAsignado;

    @Column(nullable = false)
    @Builder.Default
    private boolean personalizado = false;

    @Column(name = "observacion_personalizado", length = 500)
    private String observacionPersonalizado;
}
