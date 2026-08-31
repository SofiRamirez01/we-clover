package com.weclover.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weclover.backend.entity.ArticuloProveedor;
import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.Proveedor;

public interface ArticuloProveedorRepository extends JpaRepository<ArticuloProveedor, Long> {

    boolean existsByProveedorAndPaletaColor(Proveedor proveedor, PaletaColores paletaColor);

    Optional<ArticuloProveedor> findByProveedorAndPaletaColor(Proveedor proveedor, PaletaColores paletaColor);

    /** Todos los artículos de un color (activos e inactivos), para el panel de detalle. */
    List<ArticuloProveedor> findByPaletaColor(PaletaColores paletaColor);

    /** Usado para el conteo de "N proveedores" en la card del color, y por el futuro
     *  Planificador de Compras (M3) para resolver, dado un color, qué proveedores y precios
     *  existen (ver doc/pantallas-pendientes.md). */
    List<ArticuloProveedor> findByPaletaColorAndActivoTrue(PaletaColores paletaColor);

    List<ArticuloProveedor> findByProveedor(Proveedor proveedor);

    /** A lo sumo una fila por color (ver ArticuloProveedor.preferido) — usado por el resumen
     *  del Planificador de Compras (Fase 3) para elegir qué precio mostrar como estimado. */
    Optional<ArticuloProveedor> findByPaletaColorAndPreferidoTrue(PaletaColores paletaColor);
}
