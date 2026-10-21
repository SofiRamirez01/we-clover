package com.weclover.backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.weclover.backend.dto.pedido.OpcionMolderiaResponse;
import com.weclover.backend.dto.pedido.PedidosPorEstado;
import com.weclover.backend.dto.pedido.UnidadesPorEstadoYTipoPrenda;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.Pedido;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /**
     * Filtros que comparten el listado de Base Ventas y sus contadores (ver
     * PedidoListadoService): promoción (prefijo "AAAA-" del Nº de ficha), rango de fecha de venta
     * y, para un vendedor, solo sus propios pedidos. Cada parámetro null = sin filtrar.
     */
    String FILTROS_PROMOCION_FECHA_VENDEDOR =
        "(:prefijoPromocion IS NULL OR p.codigoInterno LIKE :prefijoPromocion) "
        + "AND (:desde IS NULL OR p.fechaVenta >= :desde) "
        + "AND (:hasta IS NULL OR p.fechaVenta <= :hasta) "
        + "AND (:idVendedor IS NULL OR p.creadoPor.id = :idVendedor) ";

    /** Además de los anteriores: búsqueda (ya con los comodines escapados con '!'), estado,
     *  "solo activos" y moldería – tipo de prenda (alguna prenda habilitada del pedido). La
     *  columna usa una collation _ai_ci: el LIKE no distingue mayúsculas ni tildes. */
    String FILTROS_LISTADO = FILTROS_PROMOCION_FECHA_VENDEDOR
        + "AND (:buscar IS NULL OR p.codigoInterno LIKE :buscar ESCAPE '!' "
        + "     OR p.colegio.nombre LIKE :buscar ESCAPE '!' "
        + "     OR p.colegio.localidad LIKE :buscar ESCAPE '!' "
        + "     OR p.creadoPor.nombre LIKE :buscar ESCAPE '!') "
        + "AND (:estado IS NULL OR p.estadoActual = :estado) "
        + "AND (:soloActivos = false OR p.estadoActual NOT IN :estadosCerrados) "
        + "AND (:idPatronCorte IS NULL OR EXISTS (SELECT 1 FROM Producto pr WHERE pr.pedido = p "
        + "     AND pr.habilitado = true AND pr.patronCorte.id = :idPatronCorte "
        + "     AND pr.tipoPrenda.id = :idTipoPrenda)) ";

    boolean existsByCodigoInterno(String codigoInterno);

    boolean existsByCodigoInternoAndIdNot(String codigoInterno, Long id);

    /** Pedido ya importado desde este lead de Kommo (ver PedidoImportRowService.validarFila). */
    Optional<Pedido> findByKommoLeadId(Long kommoLeadId);

    /** Filtro por estado de la Pantalla de Producción (ver ProduccionService). */
    List<Pedido> findByEstadoActualIn(List<EstadoPedido> estados);

    /** Pedidos de una tanda (ver TandaService). */
    List<Pedido> findByTanda_Id(Long idTanda);

    /** Todos los pedidos con tanda asignada, para derivar el estado de cada tanda en una sola
     *  consulta (ver TandaService). */
    List<Pedido> findByTandaIsNotNull();

    /** Listado paginado de Base Ventas (ver FILTROS_LISTADO). El orden viene en el Pageable
     *  (ver OrdenListadoPedidos): por eso la consulta no tiene ORDER BY propio. */
    @Query(
        value = "SELECT p FROM Pedido p WHERE " + FILTROS_LISTADO,
        countQuery = "SELECT COUNT(p) FROM Pedido p WHERE " + FILTROS_LISTADO)
    Page<Pedido> buscarListado(
        @Param("prefijoPromocion") String prefijoPromocion,
        @Param("desde") LocalDate desde,
        @Param("hasta") LocalDate hasta,
        @Param("idVendedor") Long idVendedor,
        @Param("buscar") String buscar,
        @Param("estado") EstadoPedido estado,
        @Param("soloActivos") boolean soloActivos,
        @Param("estadosCerrados") List<EstadoPedido> estadosCerrados,
        @Param("idPatronCorte") Long idPatronCorte,
        @Param("idTipoPrenda") Long idTipoPrenda,
        Pageable pageable);

    /** Pedidos por estado para los contadores del listado (ver FILTROS_PROMOCION_FECHA_VENDEDOR). */
    @Query("SELECT new com.weclover.backend.dto.pedido.PedidosPorEstado(p.estadoActual, COUNT(p)) "
        + "FROM Pedido p WHERE " + FILTROS_PROMOCION_FECHA_VENDEDOR
        + "GROUP BY p.estadoActual")
    List<PedidosPorEstado> contarPorEstado(
        @Param("prefijoPromocion") String prefijoPromocion,
        @Param("desde") LocalDate desde,
        @Param("hasta") LocalDate hasta,
        @Param("idVendedor") Long idVendedor);

    /** Unidades de las prendas habilitadas por estado del pedido y tipo de prenda, para los
     *  contadores del listado. Las prendas sin tipo de prenda no suman (no hay a qué asignarlas). */
    @Query("SELECT new com.weclover.backend.dto.pedido.UnidadesPorEstadoYTipoPrenda("
        + "p.estadoActual, tp.nombre, SUM(pr.cantidadTotal)) "
        + "FROM Producto pr JOIN pr.pedido p JOIN pr.tipoPrenda tp "
        + "WHERE pr.habilitado = true AND " + FILTROS_PROMOCION_FECHA_VENDEDOR
        + "GROUP BY p.estadoActual, tp.nombre")
    List<UnidadesPorEstadoYTipoPrenda> sumarUnidadesPorEstadoYTipoPrenda(
        @Param("prefijoPromocion") String prefijoPromocion,
        @Param("desde") LocalDate desde,
        @Param("hasta") LocalDate hasta,
        @Param("idVendedor") Long idVendedor);

    /** Prefijos de promoción ("AAAA") de los pedidos visibles, para el filtro de Base Ventas. */
    @Query("SELECT DISTINCT SUBSTRING(p.codigoInterno, 1, 4) FROM Pedido p "
        + "WHERE (:idVendedor IS NULL OR p.creadoPor.id = :idVendedor)")
    List<String> listarPrefijosPromocion(@Param("idVendedor") Long idVendedor);

    /** Combinaciones moldería – tipo de prenda de las prendas habilitadas de los pedidos visibles. */
    @Query("SELECT DISTINCT new com.weclover.backend.dto.pedido.OpcionMolderiaResponse("
        + "pc.id, pc.numeroInterno, pc.nombre, tp.id, tp.nombre) "
        + "FROM Producto pr JOIN pr.pedido p JOIN pr.patronCorte pc JOIN pr.tipoPrenda tp "
        + "WHERE pr.habilitado = true AND (:idVendedor IS NULL OR p.creadoPor.id = :idVendedor)")
    List<OpcionMolderiaResponse> listarMolderiasUsadas(@Param("idVendedor") Long idVendedor);
}
