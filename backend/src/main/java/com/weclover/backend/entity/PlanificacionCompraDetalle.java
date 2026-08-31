package com.weclover.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Una fila = cuánto de un (TipoTela, PaletaColores) hay que comprar para un Producto puntual,
 * calculada al crear la PlanificacionCompra (tela de cuerpo vía PatronCorteColor.gramos +
 * insumos secundarios ya cargados en ese momento, ver PlanificacionCompraService). No se
 * recalcula después: es una foto, no una vista derivada — por eso `cantidad`/`unidadMedida`
 * quedan copiados acá en vez de calcularse on-the-fly cada vez que se consulta.
 */
@Entity
@Table(name = "planificacion_compra_detalles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class PlanificacionCompraDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_planificacion_compra", nullable = false)
    private PlanificacionCompra planificacionCompra;

    /** Trazabilidad al pedido de origen (vía producto.getPedido()). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_tipo_tela", nullable = false)
    private TipoTela tipoTela;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paleta_color", nullable = false)
    private PaletaColores paletaColor;

    @Column(nullable = false)
    private float cantidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_medida", nullable = false, length = 20)
    private UnidadMedida unidadMedida;
}
