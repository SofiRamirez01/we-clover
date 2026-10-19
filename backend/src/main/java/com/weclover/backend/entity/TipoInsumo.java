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
 * Catálogo de tipos de insumo secundario (Capucha, Puños y cintura, Cierre, Estampado): es lo
 * que une una fila de la ficha (ProductoInsumoSecundario.tipoInsumo) con las piezas físicas de
 * la moldería que se cortan en ese insumo (Pieza.tipoInsumo), en vez de compararlas por el
 * texto libre de `descripcion`.
 *
 * `codigo` es la clave estable que usa el código (ver TipoInsumoCodigos) y no cambia nunca;
 * `nombre` es el texto visible, único sin distinguir mayúsculas (lo garantiza la collation de
 * la base). Los tipos no se borran, solo se desactivan: uno inactivo sigue valiendo en las
 * fichas y piezas que ya lo usan y solo deja de ofrecerse para elegir.
 */
@Entity
@Table(name = "tipos_insumo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class TipoInsumo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    /**
     * false para las marcas que no representan material (ej. Estampado): sus filas de ficha no
     * llevan tela, color ni cantidad, y no se pueden elegir como tela de una Pieza.
     */
    @Column(name = "consume_material", nullable = false)
    private boolean consumeMaterial;

    /**
     * Tela que se propone al agregar una fila de este tipo (ej. Jersey para Capucha). Nullable:
     * los tipos sin material no tienen. Si es una tela por peso, las filas de este tipo solo
     * pueden usar telas por peso (son insumos que se cortan).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_tipo_tela_sugerido")
    private TipoTela tipoTelaSugerido;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}
