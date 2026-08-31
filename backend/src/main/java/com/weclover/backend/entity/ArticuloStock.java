package com.weclover.backend.entity;

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
 * Envoltorio genérico sobre el artículo que se audita en Stock. Hoy es un 1 a 1 sobre
 * PaletaColores (por eso la FK es única) a propósito: el día que se sume stock de insumos
 * indirectos (hilos, friselina, cintas), esta FK pasa a ser nullable y se agrega una segunda FK
 * nullable hacia la futura entidad de insumo indirecto, con una constraint de "exactamente una
 * de las dos está seteada" — sin tener que migrar la tabla `stock`.
 */
@Entity
@Table(name = "articulos_stock")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ArticuloStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paleta_color", nullable = false, unique = true)
    private PaletaColores paletaColor;
}
