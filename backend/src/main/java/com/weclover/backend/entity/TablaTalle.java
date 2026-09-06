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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una fila de la tabla de talles de un GrupoTalle (ej. talle "12": 41cm de ancho x 56cm de
 * largo). `orden` existe para poder recorrer la tabla de menor a mayor sin asumir que `talle`
 * (la etiqueta a mostrar, ej. "1" o "12") es numéricamente comparable — ver
 * CargaTallesService.calcularTalle, que la recorre en ese orden y toma la primera fila que
 * cubre ambas medidas.
 */
@Entity
@Table(name = "tablas_talle",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tabla_talle_grupo_orden", columnNames = { "id_grupo_talle", "orden" }),
        @UniqueConstraint(name = "uk_tabla_talle_grupo_talle", columnNames = { "id_grupo_talle", "talle" })
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class TablaTalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_grupo_talle", nullable = false)
    private GrupoTalle grupoTalle;

    @Column(nullable = false, length = 10)
    private String talle;

    @Column(nullable = false)
    private int orden;

    @Column(name = "ancho_cm", nullable = false)
    private int anchoCm;

    @Column(name = "largo_cm", nullable = false)
    private int largoCm;
}
