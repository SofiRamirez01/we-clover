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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "patron_corte_colores",
    uniqueConstraints = @UniqueConstraint(name = "uk_patron_corte_orden", columnNames = { "id_patron_corte", "orden" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class PatronCorteColor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_patron_corte", nullable = false)
    private PatronCorte patronCorte;

    @Column(nullable = false)
    private int orden;

    @Column(nullable = false)
    private int gramos;

    /**
     * Nullable: las filas existentes antes de esta entrega no tienen pieza asignada (se
     * completa hacia adelante, sin migración de datos viejos — ver PiezaService/CAMBIO 2).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_pieza")
    private Pieza pieza;
}
