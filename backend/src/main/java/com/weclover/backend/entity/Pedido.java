package com.weclover.backend.entity;

import java.time.LocalDate;
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
@Table(name = "pedidos")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_colegio", nullable = false)
    private Colegio colegio;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_actual", nullable = false, length = 30)
    private EstadoPedido estadoActual;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_representante_curso", nullable = false)
    private Usuario representanteCurso;

    @Column(name = "codigo_interno", nullable = false, unique = true, length = 50)
    private String codigoInterno;

    @Column(nullable = false, length = 100)
    private String curso;

    @Column(name = "cant_alumnos", nullable = false)
    private int cantAlumnos;

    @Column(length = 500)
    private String observaciones;

    @Column(name = "fecha_venta", nullable = false)
    private LocalDate fechaVenta;

    @Column(name = "fecha_estimada_entrega", nullable = false)
    private LocalDate fechaEstimadaEntrega;

    @Column(name = "pago_inicial", nullable = false)
    @Builder.Default
    private float pagoInicial = 0f;

    /** null = prioridad automática (rank por % de pago, ver EstadoPedidoService). Con valor,
     *  queda fijada a mano y no se recalcula sola hasta que se vuelva a poner en null. */
    @Column(name = "prioridad_manual")
    private Integer prioridadManual;

    /** Quién coordina la carga de talles del curso (alumno/adulto). Nullable: dato nuevo, opcional. */
    @Enumerated(EnumType.STRING)
    @Column(name = "responsable_curso", length = 20)
    private ResponsableCurso responsableCurso;

    @Column(name = "contrato_firmado", nullable = false)
    @Builder.Default
    private boolean contratoFirmado = false;

    /** Cantidad de cuotas del plan de pago. Nullable: dato nuevo, no todos los pedidos lo cargan. */
    @Column(name = "cantidad_cuotas")
    private Integer cantidadCuotas;

    /**
     * Monto de referencia (ver PedidoImportService) para pedidos importados con más de un tipo
     * de prenda, donde no se puede desglosar el costo real por prenda sin inventar datos: el
     * Excel de origen solo trae un total/precio unitario blended para todo el pedido. Se usa como
     * respaldo de precioTotal en PedidoService.construirRespuesta mientras los Producto.costo
     * reales sigan en 0; una vez que se completan a mano deja de usarse (no se borra, queda como
     * dato histórico de la importación).
     */
    @Column(name = "monto_referencia_importado")
    private Float montoReferenciaImportado;

    /**
     * "Precio Unitario" tal cual venía en el Excel de Kommo, para pedidos importados con más de
     * un tipo de prenda (ver Pedido.montoReferenciaImportado). No es derivable como
     * montoReferenciaImportado / unidades — en la práctica Kommo lo carga como precio por
     * alumno/paquete, no por prenda física, así que perderlo sería perder un dato real de
     * negocio. Puramente informativo: no se usa en ningún cálculo de precioTotal/saldo, solo se
     * expone para mostrarlo en vez de un "precio unitario" por prenda que no existe en este caso.
     */
    @Column(name = "precio_unitario_referencia_importado")
    private Float precioUnitarioReferenciaImportado;

    @CreatedDate
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @LastModifiedDate
    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_vendedor", nullable = false)
    private Usuario creadoPor;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<HistorialEstadoPedido> historial = new ArrayList<>();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Producto> productos = new ArrayList<>();
}
