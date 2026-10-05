package com.weclover.backend.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.weclover.backend.dto.reportes.UnidadesPorEstado;
import com.weclover.backend.dto.reportes.UnidadesPorIndiceMes;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.Producto;

/**
 * Queries agregadas del reporte de producción (solo lectura, mismo criterio que
 * ReporteVentasRepository). Siempre prendas habilitadas, no-Bandera, de pedidos en :estados.
 */
public interface ReporteProduccionRepository extends Repository<Producto, Long> {

    /** Foto actual: unidades por Producto.estadoProduccion. Los estados sin prendas no vienen
     *  (los completa en 0 ReporteService). */
    @Query("SELECT new com.weclover.backend.dto.reportes.UnidadesPorEstado(p.estadoProduccion, sum(p.cantidadTotal)) "
        + "FROM Producto p JOIN p.pedido pe JOIN p.tipoPrenda tp "
        + "WHERE p.habilitado = true AND p.estadoProduccion IS NOT NULL "
        + "AND lower(tp.nombre) <> 'bandera' "
        + "AND pe.estadoActual IN :estados "
        + "AND (:idTipoPrenda IS NULL OR tp.id = :idTipoPrenda) "
        + "GROUP BY p.estadoProduccion")
    List<UnidadesPorEstado> unidadesPorEstado(
        @Param("estados") Collection<EstadoPedido> estados,
        @Param("idTipoPrenda") Long idTipoPrenda);

    /**
     * Unidades que ENTRARON a TERMINADO (estadoAnterior distinto de TERMINADO) con fechaHora en
     * [desde, hastaExclusivo), agrupadas por mes.
     *
     * El mes se obtiene como timestampdiff(month, :inicioPrimerMes, fechaHora) y no con
     * year()/month() a propósito: la base guarda los datetime corridos respecto de la hora local
     * (el driver convierte a UTC), así que year()/month() sobre la columna pondría en el mes
     * siguiente un movimiento de las últimas horas del último día. Comparando contra un
     * parámetro, que el driver corre igual que la columna, el corte de mes queda en hora local
     * sin depender de cómo esté configurada la zona horaria. :inicioPrimerMes debe ser el día 1
     * a las 00:00 del mes de :desde.
     */
    @Query("SELECT new com.weclover.backend.dto.reportes.UnidadesPorIndiceMes("
        + "cast(timestampdiff(month, :inicioPrimerMes, m.fechaHora) as Integer), "
        + "sum(coalesce(m.unidades, p.cantidadTotal))) "
        + "FROM MovimientoEstado m JOIN m.producto p JOIN p.pedido pe JOIN p.tipoPrenda tp "
        + "WHERE m.estadoNuevo = com.weclover.backend.entity.EstadoProduccion.TERMINADO "
        + "AND (m.estadoAnterior IS NULL OR m.estadoAnterior <> com.weclover.backend.entity.EstadoProduccion.TERMINADO) "
        + "AND m.fechaHora >= :desde AND m.fechaHora < :hastaExclusivo "
        + "AND p.habilitado = true "
        + "AND lower(tp.nombre) <> 'bandera' "
        + "AND pe.estadoActual IN :estados "
        + "AND (:idTipoPrenda IS NULL OR tp.id = :idTipoPrenda) "
        + "GROUP BY 1")
    List<UnidadesPorIndiceMes> terminadasPorMes(
        @Param("estados") Collection<EstadoPedido> estados,
        @Param("inicioPrimerMes") LocalDateTime inicioPrimerMes,
        @Param("desde") LocalDateTime desde,
        @Param("hastaExclusivo") LocalDateTime hastaExclusivo,
        @Param("idTipoPrenda") Long idTipoPrenda);
}
