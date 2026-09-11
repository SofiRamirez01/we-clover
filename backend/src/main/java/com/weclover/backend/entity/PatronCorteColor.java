package com.weclover.backend.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
     * Piezas físicas puestas sobre la imagen del patrón para este color, cada una con su pin
     * (ver PatronCortePosicionPieza) — reemplaza al viejo campo singular `pieza` (agregado en
     * una sesión anterior y nunca usado): un mismo color casi siempre corresponde a varias
     * piezas físicas distintas, no a una sola.
     */
    @OneToMany(mappedBy = "patronCorteColor", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Builder.Default
    private List<PatronCortePosicionPieza> posicionesPieza = new ArrayList<>();
}
