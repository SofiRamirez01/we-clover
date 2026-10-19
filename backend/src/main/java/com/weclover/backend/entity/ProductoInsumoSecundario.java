package com.weclover.backend.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Insumo secundario de un Producto puntual (ej. el cierre de una Campera, la capucha en
 * Jersey de un Buzo, los puños en Ribb). Reemplaza al viejo `Producto.colorCierre`.
 *
 * Qué insumo es cada fila lo dice `tipoInsumo` (Capucha, Puños y cintura, Cierre, Estampado) —
 * no `tipoTela`: dos insumos distintos de un mismo producto pueden usar la misma tela (ej.
 * capucha y cuello, ambos en Jersey) — ni el texto de `descripcion`, que solo identifica a los
 * insumos libres (sin tipo). Sin restricción de unicidad a propósito: un producto puede tener
 * más de una fila del mismo tipo (ej. dos de Puños y cintura de distinto color, uno por puño).
 */
@Entity
@Table(name = "producto_insumos_secundarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ProductoInsumoSecundario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    /**
     * Qué insumo es esta fila, del catálogo (ver TipoInsumo). Null = insumo libre, identificado
     * solo por `descripcion` (ej. "Bolsillo"). Un producto puede tener varias filas del mismo
     * tipo (ej. dos de Puños y cintura, de distinto color).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_tipo_insumo")
    private TipoInsumo tipoInsumo;

    /** Texto visible de la fila (ej. "Capucha", "Puños y cintura", "Cierre") y lo único que
     *  identifica a un insumo libre. Para las filas con tipoInsumo coincide con su nombre. */
    @Column(nullable = false, length = 100)
    private String descripcion;

    /**
     * Nullable: habilita filas-flag puras (ej. descripcion="Estampado", con tipoTela/color/
     * cantidad en null) que no representan consumo de material sino solo la marca de que la
     * prenda "lleva" ese insumo (ver EtapaProduccionAplicabilidad, aplicabilidad de la etapa
     * ESTAMPADO). Para un insumo cargado a mano con tela/color reales, se sigue completando
     * igual que antes.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_tipo_tela")
    private TipoTela tipoTela;

    /** Debe pertenecer al mismo tipoTela de esta fila (se valida en el service). Nullable por
     *  el mismo motivo que tipoTela. */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_paleta_color")
    private PaletaColores color;

    /** Gramos por prenda si tipoTela.esPorPeso, o unidades por prenda si no (ej. 1 para un
     *  cierre). Nullable por el mismo motivo que tipoTela (ver comentario de esa columna). */
    @Column
    private Float cantidad;

    /**
     * true si alguien escribió `cantidad` a mano; false si es el valor sugerido por el sistema
     * (ver RepartoGramosInsumo: mientras ninguna fila de un tipo esté editada a mano, sus gramos
     * se reparten solos al agregar o quitar filas o al cambiar la tela). Nullable en la base
     * solo para las filas anteriores a esta columna: null = todavía sin evaluar, lo completa la
     * migración de arranque (TipoInsumoMigracionService) y hasta entonces cuenta como false.
     */
    @Column(name = "cantidad_editada_manualmente")
    @Builder.Default
    private Boolean cantidadEditadaManualmente = false;

    /**
     * Pines de la moldería que cubre esta fila, cuando el producto tiene dos o más filas del
     * mismo tipo (ver ProductoInsumoPieza y AsignacionInsumoPieza). Se borran con la fila.
     */
    @OneToMany(mappedBy = "productoInsumo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductoInsumoPieza> asignacionesPieza = new ArrayList<>();
}
