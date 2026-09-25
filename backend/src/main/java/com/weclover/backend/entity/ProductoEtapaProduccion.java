package com.weclover.backend.entity;

import java.time.LocalDate;

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
 * Seguimiento de producción por etapa de un Producto no-Bandera (ver Producto.etapas). Solo
 * aplica a productos no-Bandera — Bandera tiene su propio flujo (ver Producto.estadoBandera).
 * Sirve también como registro de quién/cuándo marcó cada etapa (no hay un
 * HistorialEstadoProducto separado: esta fila, con fechaCompletado/empleado, alcanza).
 */
@Entity
@Table(name = "producto_etapa_produccion", uniqueConstraints = {
    @UniqueConstraint(name = "uk_producto_etapa", columnNames = { "producto_id", "etapa" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ProductoEtapaProduccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EtapaProduccion etapa;

    @Column(nullable = false)
    @Builder.Default
    private boolean completado = false;

    @Column(name = "fecha_completado")
    private LocalDate fechaCompletado;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_empleado")
    private Usuario empleado;
}
