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
 * Registro histórico (append-only, una fila por cada marcado/desmarcado) de cambios de etapa de
 * producción — a diferencia de ProductoEtapaProduccion, que solo guarda el valor ACTUAL de cada
 * etapa (se pisa en cada PUT), esta tabla nunca se actualiza ni se borra: cada llamada a
 * ProductoEtapaProduccionService.marcarEtapa/marcarEtapasBulk agrega una fila nueva, aunque el
 * valor no haya cambiado. Se muestra unificado con HistorialEstadoPedido en el modal de
 * "Historial de cambios" de Base de Ventas (ver PedidoService.listarHistorial).
 */
@Entity
@Table(name = "historial_etapa_produccion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class HistorialEtapaProduccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EtapaProduccion etapa;

    /** Valor nuevo de "completado" en el momento de este cambio (true = se marcó, false = se
     *  desmarcó). */
    @Column(nullable = false)
    private boolean completado;

    @Column(name = "fecha_cambio", nullable = false)
    private LocalDateTime fechaCambio;

    /** Quién hizo el cambio (el usuario logueado que tocó el checkbox/carga masiva) — siempre
     *  hay un actor humano acá, a diferencia de HistorialEstadoPedido.modificadoPor (que puede
     *  ser null para transiciones automáticas de Pedido). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_modifico", nullable = false)
    private Usuario modificadoPor;

    /** Empleado de planta asignado a la etapa en el momento de este cambio (puede ser distinto
     *  del actor que hizo el click) — snapshot histórico, no se actualiza retroactivamente. */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_empleado")
    private Usuario empleado;
}
