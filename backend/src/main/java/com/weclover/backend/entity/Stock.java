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
 * Valor actual de stock de un artículo por proveedor — no es un historial de movimientos, es
 * el resultado de la última auditoría física: cargar una cantidad nueva pisa la anterior.
 * Puede haber varias filas para el mismo ArticuloStock, una por cada proveedor.
 *
 * `proveedor` es nullable a propósito (decisión confirmada con el negocio): permite auditar
 * "hay tanta cantidad de esta tela" sin saber con certeza de qué proveedor es (ej. telas viejas
 * en depósito sin trazabilidad de compra). La unique constraint de la tabla NO alcanza a cubrir
 * ese caso — en MySQL/InnoDB una unique key no considera dos NULL iguales, así que permitiría
 * varias filas "sin proveedor" para el mismo artículo — por eso "a lo sumo una fila sin
 * proveedor por artículo" se hace cumplir a mano en StockService (busca-o-crea antes de
 * insertar, igual que para un proveedor puntual).
 */
@Entity
@Table(name = "stock",
    uniqueConstraints = @UniqueConstraint(name = "uk_stock_articulo_proveedor", columnNames = { "id_articulo_stock", "id_proveedor" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_articulo_stock", nullable = false)
    private ArticuloStock articulo;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_proveedor", nullable = true)
    private Proveedor proveedor;

    @Column(nullable = false)
    private float cantidad;

    @Column(name = "fecha_ultima_actualizacion", nullable = false)
    private LocalDateTime fechaUltimaActualizacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario_actualizador", nullable = false)
    private Usuario actualizadoPor;
}
