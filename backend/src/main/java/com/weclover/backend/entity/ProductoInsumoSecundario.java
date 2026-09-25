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
 * Insumo secundario de un Producto puntual (ej. el cierre de una Campera, la capucha en
 * Jersey de un Buzo, los puños en Ribb). Reemplaza al viejo `Producto.colorCierre`.
 *
 * `descripcion` identifica de qué insumo se trata (ej. "Capucha", "Puños y cintura",
 * "Cierre", o lo que haya escrito el usuario para uno libre) — no `tipoTela`: dos insumos
 * distintos de un mismo producto pueden usar la misma tela (ej. capucha y cuello, ambos en
 * Jersey). Sin restricción de unicidad a propósito: un producto puede tener más de una fila
 * con la misma descripcion (ej. dos "Puños y cintura" de distinto color, uno por puño) — lo
 * que identifica a cada fila puntual es el conjunto (descripcion, tipoTela, color), no un
 * campo por sí solo, y eso lo decide el frontend armando la lista (ver
 * ProductoService.actualizarInsumosSecundarios).
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

    /** Qué es este insumo (ej. "Capucha", "Puños y cintura", "Cierre", o texto libre). Ver
     *  el comentario de la clase: es la identidad real de la fila, no tipoTela. */
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
}
