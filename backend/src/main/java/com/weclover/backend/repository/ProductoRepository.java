package com.weclover.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.weclover.backend.entity.Producto;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findByPedidoIdAndHabilitadoTrue(Long idPedido);

    /** Todas las prendas habilitadas de los pedidos de una tanda — punto de entrada previsto
     *  para que el corte (M4/nesting) tome "todos los productos de la tanda X". */
    List<Producto> findByPedido_Tanda_IdAndHabilitadoTrue(Long idTanda);

    /** Productos sin Producto.estadoProduccion asignado (ver
     *  ProductoEtapaProduccionService.inicializarEstadosFaltantes). Incluye Bandera, que siempre
     *  queda en null — el servicio la saltea. */
    List<Producto> findByEstadoProduccionIsNullAndHabilitadoTrue();

    /**
     * Candidatos a incluir en una PlanificacionCompra. Entran los productos habilitados de:
     * - pedidos LISTO_PARA_PRODUCCION/EN_PRODUCCION/TERMINADO (regla original: excluye
     *   PRESUPUESTADO/SENADO, ENTREGADO y CANCELADO), y
     * - pedidos que ya están en una tanda y todavía no se entregaron ni cancelaron, sea cual
     *   sea su estado — se planifica por tanda, y una tanda puede incluir pedidos que todavía
     *   no están listos (decisión del negocio).
     *
     * Ya no filtra por fecha de entrega: la fecha dejó de ser el criterio principal y pasó a
     * ser un filtro más de la pantalla (junto con tanda, pagos y tipo de prenda).
     */
    @Query("SELECT p FROM Producto p "
        + "WHERE p.habilitado = true "
        + "AND (:idColegio IS NULL OR p.pedido.colegio.id = :idColegio) "
        + "AND (p.pedido.estadoActual IN (com.weclover.backend.entity.EstadoPedido.LISTO_PARA_PRODUCCION, "
        + "com.weclover.backend.entity.EstadoPedido.EN_PRODUCCION, com.weclover.backend.entity.EstadoPedido.TERMINADO) "
        + "OR (p.pedido.tanda IS NOT NULL AND p.pedido.estadoActual NOT IN ("
        + "com.weclover.backend.entity.EstadoPedido.ENTREGADO, com.weclover.backend.entity.EstadoPedido.CANCELADO)))")
    List<Producto> buscarElegiblesPlanificacion(@Param("idColegio") Long idColegio);
}
