package com.weclover.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.weclover.backend.entity.Producto;
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

    /** Preparación del corte: inicializa las asignaciones de pines de todas las filas de esos
     *  productos en una sola consulta (lo que devuelve no se usa). */
    @Query("SELECT DISTINCT i FROM ProductoInsumoSecundario i "
        + "LEFT JOIN FETCH i.asignacionesPieza a LEFT JOIN FETCH a.posicionPieza "
        + "WHERE i.producto IN :productos")
    List<ProductoInsumoSecundario> cargarAsignaciones(@Param("productos") List<Producto> productos);
}
