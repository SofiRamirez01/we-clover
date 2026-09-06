package com.weclover.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Catálogo de grupos de talles (ej. "Campera/Buzo", "Chomba/Remera") — mismo criterio que
 * TipoTela: configurable en base, no hardcodeado. Cada TipoPrenda que usa talles apunta a uno
 * de estos grupos (ver TipoPrenda.grupoTalle); una TipoPrenda sin talle (ej. Bandera) no apunta
 * a ninguno.
 */
@Entity
@Table(name = "grupos_talle")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class GrupoTalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String nombre;
}
