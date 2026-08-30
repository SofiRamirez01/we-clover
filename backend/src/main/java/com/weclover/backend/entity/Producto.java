package com.weclover.backend.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "productos")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_pedido", nullable = false)
    private Pedido pedido;

    /**
     * Nullable a nivel de base porque productos ya existentes con el viejo tipo_prenda
     * en texto libre no se pudieron mapear con certeza al catálogo nuevo (ver
     * doc/pantallas-pendientes.md). Para productos nuevos, el DTO de creación lo exige.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_tipo_prenda")
    private TipoPrenda tipoPrenda;

    /**
     * Nullable a nivel de base por el mismo motivo que tipoPrenda (productos legacy sin
     * patrón asignado). Para productos nuevos, el DTO de creación lo exige: define las
     * posiciones de color que se completan con el modal de gotero (ver ProductoColor).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_patron_corte")
    private PatronCorte patronCorte;

    /**
     * Tela de esta prenda puntual (no la del catálogo de colores: ver TipoTela). Nullable
     * porque productos legacy no lo tienen; para Buzo/Remera/Chomba/Campera/Bandera se
     * completa solo al crear el pedido con un default (ver PedidoService) y queda editable
     * desde Ficha Técnica. Solo se aceptan filas de TipoTela con telaCuerpo=true (se valida
     * en ProductoService.actualizarTipoTela).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_tipo_tela")
    private TipoTela tipoTela;

    @Column(name = "cantidad_total", nullable = false)
    private int cantidadTotal;

    /**
     * Estado de producción propio de esta prenda (mismo enum que Pedido.estadoActual, para
     * seguimiento más fino dentro de la producción). El estado del pedido se sigue manejando
     * aparte como el estado "general" — cambiar el de una prenda no lo modifica.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_actual", nullable = false, length = 30)
    private EstadoPedido estadoActual;

    @Column(nullable = false)
    private float costo;

    @Column(length = 500)
    private String observaciones;

    @Column(name = "imagen_diseno_url", length = 500)
    private String imagenDisenoUrl;

    @CreatedDate
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductoColor> colores = new ArrayList<>();

    /**
     * Insumos secundarios de esta prenda puntual (ej. el color de cierre de una Campera).
     * Reemplaza al viejo campo colorCierre — como mucho una fila por TipoTela distinto.
     */
    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductoInsumoSecundario> insumosSecundarios = new ArrayList<>();
}
