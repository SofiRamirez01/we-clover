package com.weclover.backend.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cabecera de una planificación de compra (Fase 3). Dos estados: BORRADOR (en curso, puede
 * tener nombre/fechas vacíos y productos con diseño incompleto — ver `productosBorrador`, que
 * no calcula nada, solo guarda la intención de selección) y CONFIRMADA ("foto" real, con
 * `detalles` ya calculados — ver PlanificacionCompraService.confirmar). Los `detalles` no se
 * recalculan después de confirmar, aunque cambie el patrón de corte o los insumos secundarios
 * del producto.
 */
@Entity
@Table(name = "planificaciones_compra")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class PlanificacionCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPlanificacionCompra estado;

    /**
     * Etiqueta libre del período, ej. "Semana 25/08 al 31/08". Sin restricción de unicidad.
     * Puede quedar vacío ("") mientras es un borrador; se exige recién al confirmar (ver
     * PlanificacionCompraService.confirmar) — por eso no lleva `nullable = false`.
     */
    @Column(length = 150)
    private String nombre;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    /**
     * Rango de Pedido.fechaEstimadaEntrega usado para elegir los productos incluidos. Nullable
     * por el mismo motivo que `nombre`: un borrador recién creado puede no tener fechas
     * todavía. Obligatorio al confirmar.
     */
    @Column(name = "fecha_desde")
    private LocalDate fechaDesde;

    @Column(name = "fecha_hasta")
    private LocalDate fechaHasta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_creador", nullable = false)
    private Usuario creadoPor;

    /** Solo tiene filas mientras estado=BORRADOR (ver PlanificacionCompraService.confirmar, que
     *  las vacía al convertir a CONFIRMADA). No calcula cantidad/tipoTela/color — eso recién
     *  tiene sentido cuando el producto está confirmado como completo, ver `detalles`. */
    @OneToMany(mappedBy = "planificacionCompra", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PlanificacionCompraProductoBorrador> productosBorrador = new ArrayList<>();

    /** Solo tiene filas mientras estado=CONFIRMADA. */
    @OneToMany(mappedBy = "planificacionCompra", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PlanificacionCompraDetalle> detalles = new ArrayList<>();
}
