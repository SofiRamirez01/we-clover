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
 * `segmentosBaseJson` guarda la definición por segmentos del contorno del talle base (talleBase,
 * que debe pertenecer a grupoTalle — validado en PiezaService) que cargó el usuario, como texto
 * JSON: se conserva acá (y no en PiezaTalle) porque es la única representación "editable" del
 * contorno base — la que reabre el editor de segmentos — mientras que PiezaTalle solo guarda
 * vértices ya tesselados, para el base talle y para cada talle graduado del grupo.
 *
 * El resto de la geometría resuelta (coordenadas/área/ancho/largo/perímetro), tanto del talle
 * base como del resto de los talles del grupo, vive en PiezaTalle (una fila por talle resuelto,
 * incluido el base con esBase=true) — ver Requisito 4.1 Parte 3. Las columnas
 * coordenadas_base_json/ancho_base_cm/largo_base_cm siguen físicamente en la tabla piezas
 * (migración manual, no se tocan hasta confirmar que la migración a PiezaTalle está OK) pero ya
 * no se mapean acá: nada en el código las lee ni las escribe.
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
