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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vincula un Proveedor con un color de la carta (PaletaColores) al que puede proveer, con su
 * unidad de compra y precio estimado. La unicidad es por (proveedor, color) — NO global: puede
 * (y conviene) haber varios proveedores cargados para el mismo color, justamente para poder
 * comparar precio entre ellos al planificar una compra (ver doc/pantallas-pendientes.md, M3).
 */
@Entity
@Table(name = "articulos_proveedor",
    uniqueConstraints = @UniqueConstraint(name = "uk_articulo_proveedor_color", columnNames = { "id_proveedor", "id_paleta_color" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ArticuloProveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_proveedor", nullable = false)
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paleta_color", nullable = false)
    private PaletaColores paletaColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_medida", nullable = false, length = 20)
    private UnidadMedida unidadMedida;

    /** Opcional: no siempre se conoce el precio al momento de cargar el artículo. */
    @Column(name = "precio_estimado")
    private Float precioEstimado;

    /**
     * A lo sumo un ArticuloProveedor con preferido=true por PaletaColores (se hace cumplir en
     * ArticuloProveedorService, no con una unique constraint de base — la regla es "a lo sumo
     * uno true", no "exactamente uno", así que una constraint clásica no alcanza). Usado por
     * el Planificador de Compras (Fase 3) para elegir qué precio mostrar como estimado cuando
     * hay varios proveedores cargados para el mismo color.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean preferido = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}
