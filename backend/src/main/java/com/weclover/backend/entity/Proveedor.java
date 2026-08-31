package com.weclover.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Catálogo de proveedores de insumos. No forma parte del diagrama de clases original de
 * CLAUDE.md (ahí `MateriaPrima.proveedor` era solo un string libre) — se agregó como entidad
 * propia porque el negocio necesita reutilizar el mismo proveedor entre varios artículos
 * (ver ArticuloProveedor) y comparar precios entre proveedores para un mismo color.
 */
@Entity
@Table(name = "proveedores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Solo dígitos (11), sin guiones — el guionado es responsabilidad de la UI al mostrarlo. */
    @Column(nullable = false, unique = true, length = 11)
    private String cuit;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}
