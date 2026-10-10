package com.weclover.backend.entity;

import java.time.LocalDateTime;

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
 * Una "guardada" del popup de priorización (append-only): todos los cambios de tanda de esa vez
 * (ver HistorialTandaPedido.sesion) cuelgan de ella.
 *
 * El cambio de orden de la cola se registra acá, a nivel sesión: ordenAnteriorJson/
 * ordenNuevoJson son la lista de tandas abiertas antes y después, como
 * [{"idTanda":1,"nombre":"A"},...] (mismo criterio de JSON-en-TEXT que Pieza.segmentosBaseJson).
 * Ambos null si la sesión no cambió el orden. El nombre va copiado para que renombrar o borrar
 * una tanda después no altere lo registrado.
 */
@Entity
@Table(name = "sesion_priorizacion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class SesionPriorizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Column(length = 500)
    private String nota;

    @Column(name = "orden_anterior_json", columnDefinition = "TEXT")
    private String ordenAnteriorJson;

    @Column(name = "orden_nuevo_json", columnDefinition = "TEXT")
    private String ordenNuevoJson;
}
