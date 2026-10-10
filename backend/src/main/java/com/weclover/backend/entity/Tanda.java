package com.weclover.backend.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
 * Grupo de pedidos completos que se producen juntos (se cortan y confeccionan en conjunto) —
 * reemplaza a la prioridad numérica por pedido. Un pedido pertenece a una sola tanda o a
 * ninguna (ver Pedido.tanda).
 *
 * No tiene columna de estado: PLANIFICADA/EN_PRODUCCION/CERRADA se deriva de sus pedidos (ver
 * TandaEstadoCalculador), igual que la posición visible en la cola, que se calcula contando
 * solo las tandas no cerradas según `orden`.
 *
 * nombre es solo una etiqueta (letras, como en el Excel de origen): único entre las tandas no
 * cerradas — regla validada en TandaService, no con un UNIQUE de base, porque "cerrada" es
 * derivado y una letra de una tanda cerrada se puede volver a usar. Toda referencia a una tanda
 * va por id, nunca por nombre.
 */
@Entity
@Table(name = "tandas")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Tanda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 5)
    private String nombre;

    /** Posición relativa en la cola (menor = antes). No es único ni correlativo: se reescribe
     *  para las tandas abiertas en cada sesión de priorización y nunca se renumera al cerrarse
     *  una tanda. */
    @Column(nullable = false)
    private int orden;

    @CreatedDate
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_creador", nullable = false, updatable = false)
    private Usuario creadoPor;
}
