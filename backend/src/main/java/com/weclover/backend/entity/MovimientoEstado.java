package com.weclover.backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
 * Historial único (append-only) de producción de un Producto — reemplaza a la vieja tabla
 * historial_etapa_produccion. Una fila por cada:
 * - marcado/desmarcado de etapa (etapa/completado/empleado completos, aunque el estado no cambie);
 * - cambio de estado que no viene de una etapa (etapa=null): pedido marcado ENTREGADO o que sale
 *   de ENTREGADO, cambio de aplicabilidad de etapas (ej. se agregó "Estampado"), o carga
 *   retroactiva (observaciones="Carga retroactiva", usuario=null).
 *
 * estadoAnterior/estadoNuevo son null solo en filas migradas de la tabla vieja (no se conocía el
 * estado en ese momento). "Terminadas por mes" = filas con estadoNuevo=TERMINADO y estadoAnterior
 * distinto de TERMINADO. unidades es un snapshot de Producto.cantidadTotal al momento del cambio,
 * para que un reporte histórico no cambie si después se edita la cantidad del producto.
 */
@Entity
@Table(name = "movimiento_estado", indexes = {
    @Index(name = "idx_movimiento_estado_nuevo_fecha", columnList = "estado_nuevo, fecha_hora")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class MovimientoEstado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 20)
    private EstadoProduccion estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", length = 20)
    private EstadoProduccion estadoNuevo;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    /** Quién hizo el cambio. null en cambios sin actor humano directo (carga retroactiva,
     *  recálculo por cambio de aplicabilidad de etapas). */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_usuario")
    private Usuario usuario;

    /** Etapa marcada/desmarcada — null si el movimiento no viene de una etapa. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EtapaProduccion etapa;

    /** Valor nuevo de "completado" de la etapa — null si etapa es null. */
    @Column
    private Boolean completado;

    /** Empleado de planta asignado a la etapa en ese momento (snapshot) — base para el futuro
     *  reporte de productividad por empleado. */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_empleado")
    private Usuario empleado;

    @Column
    private Integer unidades;

    @Column(length = 255)
    private String observaciones;
}
