package com.weclover.backend.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.weclover.backend.dto.reportes.PedidosSinDesglosePorMes;
import com.weclover.backend.dto.reportes.UnidadesMontoPorMes;
import com.weclover.backend.dto.reportes.UnidadesMontoPorTipoPrenda;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.Producto;

/**
 * Queries agregadas (GROUP BY en la base, proyectadas a records) del reporte de ventas. Solo
 * lectura: extiende Repository y no JpaRepository a propósito, para no exponer save/delete.
 *
 * Filtro común de producto: habilitado, tipo de prenda distinto de Bandera, pedido en alguno de
 * :estados con fechaVenta en [desde, hasta], y opcionalmente un tipo de prenda puntual.
 * "Sin desglose" = pedido con montoReferenciaImportado y ningún producto con cantidad×costo > 0
 * (mismo criterio que EstadoPedidoService.calcularPrecioTotalPedido).
 */
public interface ReporteVentasRepository extends Repository<Producto, Long> {

    String FILTRO_PRODUCTO = " WHERE p.habilitado = true "
        + "AND lower(tp.nombre) <> 'bandera' "
        + "AND pe.estadoActual IN :estados "
        + "AND pe.fechaVenta BETWEEN :desde AND :hasta "
        + "AND (:idTipoPrenda IS NULL OR tp.id = :idTipoPrenda) ";

    String PEDIDO_SIN_DESGLOSE = " pe.montoReferenciaImportado IS NOT NULL "
        + "AND NOT EXISTS (SELECT 1 FROM Producto px WHERE px.pedido = pe AND px.habilitado = true "
        + "AND px.cantidadTotal * px.costo > 0) ";

    @Query("SELECT new com.weclover.backend.dto.reportes.UnidadesMontoPorMes("
        + "year(pe.fechaVenta), month(pe.fechaVenta), sum(p.cantidadTotal), sum(p.cantidadTotal * p.costo)) "
        + "FROM Producto p JOIN p.pedido pe JOIN p.tipoPrenda tp" + FILTRO_PRODUCTO
        + "GROUP BY year(pe.fechaVenta), month(pe.fechaVenta)")
    List<UnidadesMontoPorMes> unidadesMontoPorMes(
        @Param("estados") Collection<EstadoPedido> estados,
        @Param("desde") LocalDate desde,
        @Param("hasta") LocalDate hasta,
        @Param("idTipoPrenda") Long idTipoPrenda);

    @Query("SELECT new com.weclover.backend.dto.reportes.UnidadesMontoPorTipoPrenda("
        + "tp.id, tp.nombre, sum(p.cantidadTotal), sum(p.cantidadTotal * p.costo)) "
        + "FROM Producto p JOIN p.pedido pe JOIN p.tipoPrenda tp" + FILTRO_PRODUCTO
        + "GROUP BY tp.id, tp.nombre ORDER BY tp.nombre")
    List<UnidadesMontoPorTipoPrenda> unidadesMontoPorTipoPrenda(
        @Param("estados") Collection<EstadoPedido> estados,
        @Param("desde") LocalDate desde,
        @Param("hasta") LocalDate hasta,
        @Param("idTipoPrenda") Long idTipoPrenda);

    @Query("SELECT count(DISTINCT pe.id) "
        + "FROM Producto p JOIN p.pedido pe JOIN p.tipoPrenda tp" + FILTRO_PRODUCTO)
    long contarPedidos(
        @Param("estados") Collection<EstadoPedido> estados,
        @Param("desde") LocalDate desde,
        @Param("hasta") LocalDate hasta,
        @Param("idTipoPrenda") Long idTipoPrenda);

    /** Pedidos sin desglose que tienen al menos un producto que pasa el filtro común. */
    @Query("SELECT new com.weclover.backend.dto.reportes.PedidosSinDesglosePorMes("
        + "year(pe.fechaVenta), month(pe.fechaVenta), count(pe), sum(pe.montoReferenciaImportado)) "
        + "FROM Pedido pe "
        + "WHERE pe.estadoActual IN :estados AND pe.fechaVenta BETWEEN :desde AND :hasta "
        + "AND" + PEDIDO_SIN_DESGLOSE
        + "AND EXISTS (SELECT 1 FROM Producto p JOIN p.tipoPrenda tp WHERE p.pedido = pe "
        + "AND p.habilitado = true AND lower(tp.nombre) <> 'bandera' "
        + "AND (:idTipoPrenda IS NULL OR tp.id = :idTipoPrenda)) "
        + "GROUP BY year(pe.fechaVenta), month(pe.fechaVenta)")
    List<PedidosSinDesglosePorMes> pedidosSinDesglosePorMes(
        @Param("estados") Collection<EstadoPedido> estados,
        @Param("desde") LocalDate desde,
        @Param("hasta") LocalDate hasta,
        @Param("idTipoPrenda") Long idTipoPrenda);

    /** Unidades (del filtro común) que pertenecen a pedidos sin desglose — no tienen precio. */
    @Query("SELECT coalesce(sum(p.cantidadTotal), 0) "
        + "FROM Producto p JOIN p.pedido pe JOIN p.tipoPrenda tp" + FILTRO_PRODUCTO
        + "AND" + PEDIDO_SIN_DESGLOSE)
    long unidadesSinDesglose(
        @Param("estados") Collection<EstadoPedido> estados,
        @Param("desde") LocalDate desde,
        @Param("hasta") LocalDate hasta,
        @Param("idTipoPrenda") Long idTipoPrenda);
}
