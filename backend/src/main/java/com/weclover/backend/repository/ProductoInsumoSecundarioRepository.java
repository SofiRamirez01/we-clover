package com.weclover.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weclover.backend.entity.ProductoInsumoSecundario;

/**
 * Las filas de insumo se leen y se escriben siempre a través de Producto.insumosSecundarios
 * (ver ProductoService); este repositorio existe solo para las migraciones de arranque, que
 * necesitan recorrer las filas de todos los productos.
 */
public interface ProductoInsumoSecundarioRepository extends JpaRepository<ProductoInsumoSecundario, Long> {

    List<ProductoInsumoSecundario> findByTipoInsumoIsNull();

    /** Filas anteriores a la columna cantidad_editada_manualmente, todavía sin evaluar. */
    List<ProductoInsumoSecundario> findByCantidadEditadaManualmenteIsNull();
}
