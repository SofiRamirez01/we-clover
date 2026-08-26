package com.weclover.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cada fila es un color atado a una tela específica (o a "cierre"): el mismo nombre puede
 * repetirse para distintas telas (ej. "Marino" en FRIZA y otra fila "Marino" en JERSEY),
 * porque son insumos de compra distintos aunque el swatch se vea igual. Ver TipoTela.
 */
@Entity
@Table(name = "paleta_colores",
    uniqueConstraints = @UniqueConstraint(name = "uk_paleta_color_nombre_tela", columnNames = { "nombre", "tipo_tela" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class PaletaColores {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 7)
    private String hex;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_tela", nullable = false, length = 20)
    private TipoTela tipoTela;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}
