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
 * Evento (append-only) de asignación de tanda de un pedido: entra a una tanda, cambia de tanda
 * o sale de una. Forma parte del historial unificado del pedido (ver
 * PedidoService.listarHistorial).
 *
 * Las tandas anterior/nueva NO son FK a propósito: se guarda el id como dato suelto más el
 * nombre al momento del cambio, para que renombrar una tanda no altere lo registrado y para
 * poder borrar físicamente una tanda vacía que tiene historia. idTanda* null = "sin tanda".
 *
 * El bloque de snapshot es el contexto del pedido en el momento de la decisión (no se
 * recalcula nunca). Se guardan los tres requisitos de LISTO_PARA_PRODUCCION por separado, no
 * un "cumplía" derivado.
 */
@Entity
@Table(name = "historial_tanda_pedido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class HistorialTandaPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_pedido", nullable = false)
    private Pedido pedido;

    /** Null cuando el cambio no viene del popup de priorización (salida automática por
     *  cancelación del pedido). */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_sesion_priorizacion")
    private SesionPriorizacion sesion;

    @Column(name = "id_tanda_anterior")
    private Long idTandaAnterior;

    @Column(name = "nombre_tanda_anterior", length = 5)
    private String nombreTandaAnterior;

    @Column(name = "id_tanda_nueva")
    private Long idTandaNueva;

    @Column(name = "nombre_tanda_nueva", length = 5)
    private String nombreTandaNueva;

    /** Quién hizo el cambio (en la cancelación automática, quien canceló el pedido). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDateTime fecha;

    /** Obligatorio al sacar un pedido de una tanda en la que ya estaba (salvo cancelación
     *  automática); opcional en el resto. Regla validada en TandaService. */
    @Column(length = 500)
    private String motivo;

    @Column(name = "snapshot_porcentaje_pagado", nullable = false)
    private float snapshotPorcentajePagado;

    /** Puntaje sugerido (rank por % de pago) en ese momento. */
    @Column(name = "snapshot_prioridad_automatica")
    private Integer snapshotPrioridadAutomatica;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_estado_pedido", nullable = false, length = 30)
    private EstadoPedido snapshotEstadoPedido;

    @Column(name = "snapshot_diseno_completo", nullable = false)
    private boolean snapshotDisenoCompleto;

    @Column(name = "snapshot_talles_completos", nullable = false)
    private boolean snapshotTallesCompletos;

    @Column(name = "snapshot_pago_suficiente", nullable = false)
    private boolean snapshotPagoSuficiente;
}
