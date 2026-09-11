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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * El contorno resuelto de una Pieza para UN talle puntual de su GrupoTalle (incluye el propio
 * talle base: esBase=true). Un talle del grupo sin fila acá está "Pendiente" — no hace falta un
 * flag aparte para ese estado, alcanza con la ausencia de la fila.
 *
 * `coordenadasJson` guarda la lista de vértices ya tesselada (mismo formato que
 * PiezaGeometriaCalculoResponse.coordenadas): para esBase=true la calcula el servicio de
 * geometría (Shapely) a partir de Pieza.segmentosBaseJson; para el resto la calcula el frontend
 * (escalarPieza, escalado anisotrópico ancla en bounding box, sin Shapely) y este backend solo
 * la persiste tal cual, sin recalcular nada.
 *
 * editadoManualmente distingue un talle graduado automáticamente (false, se recalcula en cadena
 * cada vez que cambia la base) de uno corregido a mano en el editor de segmentos (true, queda
 * fijo hasta que se lo revierta — ver /revertir). No tiene efecto sobre la fila esBase=true.
 */
@Entity
@Table(name = "pieza_talles",
    uniqueConstraints = @UniqueConstraint(name = "uk_pieza_talle_pieza_talle", columnNames = { "id_pieza", "id_talle" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class PiezaTalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_pieza", nullable = false)
    private Pieza pieza;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_talle", nullable = false)
    private TablaTalle talle;

    @Column(name = "coordenadas_json", nullable = false, columnDefinition = "TEXT")
    private String coordenadasJson;

    @Column(name = "area_cm2", nullable = false)
    private double areaCm2;

    @Column(name = "ancho_cm", nullable = false)
    private double anchoCm;

    @Column(name = "largo_cm", nullable = false)
    private double largoCm;

    @Column(name = "perimetro_cm", nullable = false)
    private double perimetroCm;

    @Column(name = "es_base", nullable = false)
    @Builder.Default
    private boolean esBase = false;

    @Column(name = "editado_manualmente", nullable = false)
    @Builder.Default
    private boolean editadoManualmente = false;

    @Column(name = "fecha_generacion", nullable = false)
    private LocalDateTime fechaGeneracion;
}
