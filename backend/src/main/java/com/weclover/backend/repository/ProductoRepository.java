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

    /**
     * Candidatos a incluir en una PlanificacionCompra (Fase 3): filtra por
     * Pedido.fechaEstimadaEntrega (no fechaVenta), opcionalmente por colegio, y por estado del
     * pedido — solo LISTO_PARA_PRODUCCION/EN_PRODUCCION/TERMINADO (excluye PRESUPUESTADO/
     * SENADO, que antes de esta entrega no se excluían en absoluto, y ENTREGADO/CANCELADO).
     * Es un cambio de comportamiento aceptado explícitamente con el negocio (ver 6.1 de la
     * entrega de Producción): antes cualquier producto en rango de fecha aparecía acá sin
     * importar el estado del pedido.
     */
    @Query("SELECT p FROM Producto p "
        + "WHERE p.pedido.fechaEstimadaEntrega BETWEEN :desde AND :hasta "
        + "AND (:idColegio IS NULL OR p.pedido.colegio.id = :idColegio) "
        + "AND p.pedido.estadoActual IN (com.weclover.backend.entity.EstadoPedido.LISTO_PARA_PRODUCCION, "
        + "com.weclover.backend.entity.EstadoPedido.EN_PRODUCCION, com.weclover.backend.entity.EstadoPedido.TERMINADO)")
    List<Producto> buscarElegiblesPlanificacion(
        @Param("desde") LocalDate desde,
        @Param("hasta") LocalDate hasta,
        @Param("idColegio") Long idColegio);
}
