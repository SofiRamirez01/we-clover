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

/**
 * Catálogo reutilizable de piezas de moldería (ej. "Manga", "Espalda", "Capucha"): la misma
 * Pieza puede asignarse a varias posiciones de color de distintos patrones de corte (ver
 * PatronCorteColor.pieza). El nombre es único dentro de su GrupoTalle (no global): dos grupos
 * de talle distintos pueden tener cada uno su propia "Manga", con formas y medidas distintas.
 *
 * `segmentosBaseJson`/`coordenadasBaseJson` guardan el contorno del talle base (talleBase, que
 * debe pertenecer a grupoTalle — validado en PiezaService) como texto JSON: la definición por
 * segmentos que cargó el usuario, y la lista de vértices ya tesselada que devolvió el servicio
 * de geometría a partir de esos segmentos, respectivamente. anchoBaseCm/largoBaseCm son el
 * ancho/largo de esa forma base, también calculados por el servicio de geometría; una fase
 * futura los usa como base de escala para graduar automáticamente el resto de los talles.
 */
@Entity
@Table(name = "piezas",
    uniqueConstraints = @UniqueConstraint(name = "uk_pieza_grupo_talle_nombre", columnNames = { "id_grupo_talle", "nombre" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Pieza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_grupo_talle", nullable = false)
    private GrupoTalle grupoTalle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_talle_base", nullable = false)
    private TablaTalle talleBase;

    @Column(name = "segmentos_base_json", nullable = false, columnDefinition = "TEXT")
    private String segmentosBaseJson;

    @Column(name = "coordenadas_base_json", nullable = false, columnDefinition = "TEXT")
    private String coordenadasBaseJson;

    @Column(name = "ancho_base_cm", nullable = false)
    private double anchoBaseCm;

    @Column(name = "largo_base_cm", nullable = false)
    private double largoBaseCm;

    /**
     * Para la fase futura de optimización de layout de corte (algorpatronsnap.py): si la pieza
     * es simétrica, esa fase puede reflejarla en vez de necesitar una segunda pieza espejada.
     * No tiene ningún efecto en el cálculo de esta entrega (área/ancho/largo/perímetro no
     * dependen de esto).
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean simetrica = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}
