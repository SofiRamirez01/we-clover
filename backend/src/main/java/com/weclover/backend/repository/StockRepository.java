package com.weclover.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.weclover.backend.entity.ArticuloStock;
import com.weclover.backend.entity.Proveedor;
import com.weclover.backend.entity.Stock;

public interface StockRepository extends JpaRepository<Stock, Long> {

    /** Todas las filas de proveedor de un mismo artículo, para sumar el total disponible
     *  (lo va a necesitar la Fase 2, que consulta esto para descontar del planificador). */
    List<Stock> findByArticulo(ArticuloStock articulo);

    Optional<Stock> findByArticuloAndProveedor(ArticuloStock articulo, Proveedor proveedor);

    Optional<Stock> findByArticuloAndProveedorIsNull(ArticuloStock articulo);

    /** Listado completo para la grilla, con las FK anidadas (color, tela, proveedor, usuario)
     *  ya resueltas en una sola consulta para no pagar N+1 al armar cada fila. */
    @Query("""
        SELECT s FROM Stock s
        JOIN FETCH s.articulo a
        JOIN FETCH a.paletaColor p
        JOIN FETCH p.tipoTela t
        LEFT JOIN FETCH s.proveedor
        JOIN FETCH s.actualizadoPor
        ORDER BY t.nombre ASC, p.nombre ASC
        """)
    List<Stock> listarTodoParaGrilla();
}
