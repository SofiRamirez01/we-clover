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

/** Un alumno cargado dentro de una CargaTallesPedido, con sus combos de prenda en
 *  AlumnoProductoTalle. `orden` es el orden de alta (para que la tabla no reordene sola). */
@Entity
@Table(name = "alumnos_pedido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class AlumnoPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_carga_talles", nullable = false)
    private CargaTallesPedido cargaTalles;

    @Column(name = "nombre_alumno", nullable = false, length = 150)
    private String nombreAlumno;

    @Column(nullable = false)
    private int orden;
}
