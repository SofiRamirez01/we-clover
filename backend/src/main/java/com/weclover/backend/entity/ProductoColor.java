package com.weclover.backend.entity;

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
 * Color real asignado (por gotero) a una posición puntual del Patrón de Corte de un
 * Producto. patronCorteColor referencia la posición exacta (orden + gramos) del patrón
 * de corte de ese producto, no un "orden" suelto, para que quede atada al patrón vigente.
 */
@Entity
@Table(name = "producto_colores",
    uniqueConstraints = @UniqueConstraint(name = "uk_producto_patron_corte_color",
        columnNames = { "id_producto", "id_patron_corte_color" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class ProductoColor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_patron_corte_color", nullable = false)
    private PatronCorteColor patronCorteColor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_paleta_color", nullable = false)
    private PaletaColores paletaColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_deteccion", nullable = false, length = 20)
    private MetodoDeteccionColor metodoDeteccion;

    @Column(name = "coordenada_x")
    private Integer coordenadaX;

    @Column(name = "coordenada_y")
    private Integer coordenadaY;

    @Column(name = "rgb_detectado", length = 20)
    private String rgbDetectado;
}
