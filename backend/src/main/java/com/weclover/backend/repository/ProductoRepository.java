package com.weclover.backend.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.weclover.backend.entity.Producto;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findByPedidoId(Long idPedido);

    /** Candidatos a incluir en una PlanificacionCompra (Fase 3): filtra por
     *  Pedido.fechaEstimadaEntrega (no fechaVenta) y, opcionalmente, por colegio. */
    @Query("SELECT p FROM Producto p "
        + "WHERE p.pedido.fechaEstimadaEntrega BETWEEN :desde AND :hasta "
        + "AND (:idColegio IS NULL OR p.pedido.colegio.id = :idColegio)")
    List<Producto> buscarElegiblesPlanificacion(
        @Param("desde") LocalDate desde,
        @Param("hasta") LocalDate hasta,
        @Param("idColegio") Long idColegio);
}
