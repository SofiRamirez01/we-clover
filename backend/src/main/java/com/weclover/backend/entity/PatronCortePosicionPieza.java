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
 * Una Pieza física puesta sobre la imagen de un PatronCorteColor, marcada con un pin puntual
 * (no un contorno/región — eso queda para una fase futura, ver clase). La misma Pieza puede
 * repetirse en varios pines de un mismo color (ej. los dos puños) o incluso en distintos
 * colores: no hay restricción de unicidad sobre (patronCorteColor, pieza).
 *
 * coordenadaXPin/coordenadaYPin son una fracción relativa (0.0 a 1.0) del ancho/alto de la
 * imagen del patrón, no píxeles — así el pin queda bien ubicado sin importar a qué tamaño se
 * renderice esa imagen en cada pantalla.
 */
@Entity
@Table(name = "patron_corte_posiciones_pieza")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class PatronCortePosicionPieza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_patron_corte_color", nullable = false)
    private PatronCorteColor patronCorteColor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_pieza", nullable = false)
    private Pieza pieza;

    @Column(name = "coordenada_x_pin", nullable = false)
    private double coordenadaXPin;

    @Column(name = "coordenada_y_pin", nullable = false)
    private double coordenadaYPin;

    /** Texto libre opcional (ej. "Puño derecho"), solo para claridad humana — no se usa en ninguna lógica. */
    @Column(length = 150)
    private String etiqueta;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;
}
