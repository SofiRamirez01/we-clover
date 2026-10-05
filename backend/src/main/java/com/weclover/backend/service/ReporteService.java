package com.weclover.backend.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.reportes.PedidosSinDesglosePorMes;
import com.weclover.backend.dto.reportes.ProduccionPorEstadoResponse;
import com.weclover.backend.dto.reportes.ProduccionPorMesResponse;
import com.weclover.backend.dto.reportes.ReporteFiltros;
import com.weclover.backend.dto.reportes.ReporteProduccionResponse;
import com.weclover.backend.dto.reportes.ReporteVentasResponse;
import com.weclover.backend.dto.reportes.UnidadesMontoPorMes;
import com.weclover.backend.dto.reportes.UnidadesPorEstado;
import com.weclover.backend.dto.reportes.UnidadesPorIndiceMes;
import com.weclover.backend.dto.reportes.VentasPorMesResponse;
import com.weclover.backend.dto.reportes.VentasPorTipoPrendaResponse;
import com.weclover.backend.dto.reportes.VentasPresupuestadosResponse;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.EstadoProduccion;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.repository.ReporteProduccionRepository;
import com.weclover.backend.repository.ReporteVentasRepository;

import lombok.RequiredArgsConstructor;

/**
 * Módulo 5 — Reportes. Solo lectura y solo ROLE_ADMINISTRATIVO. Toda la agregación se hace en
 * la base (ver ReporteVentasRepository/ReporteProduccionRepository); acá solo se combinan esos resultados ya agrupados y se
 * completan en 0 los meses y estados sin datos.
 */
@Service
@RequiredArgsConstructor
public class ReporteService {

    /** Cantidad de meses del período por defecto (incluye el mes en curso). */
    static final int MESES_POR_DEFECTO = 12;

    /** "Vendido" = desde SENADO en adelante. CANCELADO queda afuera de todos los reportes (ver
     *  doc/pantallas-pendientes.md). */
    static final Set<EstadoPedido> ESTADOS_VENDIDO = Set.of(
        EstadoPedido.SENADO, EstadoPedido.LISTO_PARA_PRODUCCION, EstadoPedido.EN_PRODUCCION,
        EstadoPedido.TERMINADO, EstadoPedido.ENTREGADO);

    static final Set<EstadoPedido> ESTADOS_PRESUPUESTADO = Set.of(EstadoPedido.PRESUPUESTADO);

    private final ReporteVentasRepository reporteVentasRepository;
    private final ReporteProduccionRepository reporteProduccionRepository;
    private final AutorizacionService autorizacionService;

    @Transactional(readOnly = true)
    public ReporteVentasResponse ventas(LocalDate desde, LocalDate hasta, Long idTipoPrenda, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);
        ReporteFiltros filtros = resolverFiltros(desde, hasta, idTipoPrenda);

        ResumenVentas vendidos = resumirVentas(ESTADOS_VENDIDO, filtros);
        ResumenVentas presupuestados = resumirVentas(ESTADOS_PRESUPUESTADO, filtros);

        List<VentasPorTipoPrendaResponse> porTipoPrenda = reporteVentasRepository
            .unidadesMontoPorTipoPrenda(ESTADOS_VENDIDO, filtros.desde(), filtros.hasta(), filtros.idTipoPrenda())
            .stream()
            .map(fila -> new VentasPorTipoPrendaResponse(
                fila.idTipoPrenda(), fila.tipoPrenda(), valor(fila.unidades()), valor(fila.monto())))
            .toList();

        return new ReporteVentasResponse(
            filtros.desde(), filtros.hasta(), filtros.idTipoPrenda(),
            vendidos.unidades(), vendidos.monto(), vendidos.precioPromedioUnidad(), vendidos.cantidadPedidos(),
            vendidos.montoIncompleto(), vendidos.pedidosSinDesglose(),
            porTipoPrenda, vendidos.porMes(),
            new VentasPresupuestadosResponse(
                presupuestados.cantidadPedidos(), presupuestados.unidades(), presupuestados.monto(),
                presupuestados.montoIncompleto()));
    }

    /**
     * Producción solo cuenta prendas de pedidos vendidos (mismo criterio que ventas): una prenda
     * de un pedido todavía PRESUPUESTADO no está "pendiente en planta".
     */
    @Transactional(readOnly = true)
    public ReporteProduccionResponse produccion(LocalDate desde, LocalDate hasta, Long idTipoPrenda, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);
        ReporteFiltros filtros = resolverFiltros(desde, hasta, idTipoPrenda);

        Map<EstadoProduccion, Long> unidadesPorEstado = reporteProduccionRepository
            .unidadesPorEstado(ESTADOS_VENDIDO, filtros.idTipoPrenda()).stream()
            .collect(Collectors.toMap(UnidadesPorEstado::estado, fila -> valor(fila.unidades())));

        // Siempre los 9 estados, en orden de pipeline (el del enum), aunque tengan 0.
        List<ProduccionPorEstadoResponse> porEstado = Arrays.stream(EstadoProduccion.values())
            .map(estado -> new ProduccionPorEstadoResponse(estado, unidadesPorEstado.getOrDefault(estado, 0L)))
            .toList();
        long unidadesEnPlanta = porEstado.stream()
            .filter(fila -> fila.estado() != EstadoProduccion.ENTREGADO)
            .mapToLong(ProduccionPorEstadoResponse::unidades)
            .sum();

        List<YearMonth> meses = mesesDelPeriodo(filtros);
        Map<Integer, Long> terminadasPorIndice = reporteProduccionRepository
            .terminadasPorMes(
                ESTADOS_VENDIDO,
                meses.get(0).atDay(1).atStartOfDay(),
                filtros.desde().atStartOfDay(),
                filtros.hasta().plusDays(1).atStartOfDay(),
                filtros.idTipoPrenda())
            .stream()
            .collect(Collectors.toMap(UnidadesPorIndiceMes::indiceMes, fila -> valor(fila.unidades())));

        List<ProduccionPorMesResponse> terminadasPorMes = new ArrayList<>();
        for (int indice = 0; indice < meses.size(); indice++) {
            terminadasPorMes.add(new ProduccionPorMesResponse(
                meses.get(indice).toString(), terminadasPorIndice.getOrDefault(indice, 0L)));
        }

        return new ReporteProduccionResponse(
            filtros.desde(), filtros.hasta(), filtros.idTipoPrenda(),
            porEstado, unidadesEnPlanta, terminadasPorMes);
    }

    /**
     * Por defecto, los últimos 12 meses calendario terminando hoy (desde el día 1 de hace 11
     * meses). Si viene solo uno de los dos extremos, el otro se completa con el mismo criterio.
     */
    ReporteFiltros resolverFiltros(LocalDate desde, LocalDate hasta, Long idTipoPrenda) {
        LocalDate hastaEfectivo = hasta != null ? hasta : LocalDate.now();
        LocalDate desdeEfectivo = desde != null
            ? desde
            : YearMonth.from(hastaEfectivo).minusMonths(MESES_POR_DEFECTO - 1L).atDay(1);
        if (desdeEfectivo.isAfter(hastaEfectivo)) {
            throw new BusinessRuleException("La fecha 'desde' no puede ser posterior a la fecha 'hasta'");
        }
        return new ReporteFiltros(desdeEfectivo, hastaEfectivo, idTipoPrenda);
    }

    /** Todos los meses calendario que toca el período, en orden, estén o no en los datos. */
    static List<YearMonth> mesesDelPeriodo(ReporteFiltros filtros) {
        List<YearMonth> meses = new ArrayList<>();
        YearMonth ultimo = YearMonth.from(filtros.hasta());
        for (YearMonth mes = YearMonth.from(filtros.desde()); !mes.isAfter(ultimo); mes = mes.plusMonths(1)) {
            meses.add(mes);
        }
        return meses;
    }

    /**
     * Totales y serie mensual de los pedidos en esos estados. El monto de un pedido "sin
     * desglose" (ver ReporteVentasRepository) solo se puede sumar cuando no se filtra por tipo
     * de prenda: con filtro no se sabe qué parte corresponde a esa prenda, así que se excluye,
     * se marca montoIncompleto, y el precio promedio se calcula sobre las unidades con precio.
     */
    private ResumenVentas resumirVentas(Set<EstadoPedido> estados, ReporteFiltros filtros) {
        LocalDate desde = filtros.desde();
        LocalDate hasta = filtros.hasta();
        Long idTipoPrenda = filtros.idTipoPrenda();
        boolean filtraPorTipoPrenda = idTipoPrenda != null;

        Map<YearMonth, UnidadesMontoPorMes> desglosadoPorMes = reporteVentasRepository
            .unidadesMontoPorMes(estados, desde, hasta, idTipoPrenda).stream()
            .collect(Collectors.toMap(fila -> YearMonth.of(fila.anio(), fila.mes()), fila -> fila));
        Map<YearMonth, PedidosSinDesglosePorMes> sinDesglosePorMes = reporteVentasRepository
            .pedidosSinDesglosePorMes(estados, desde, hasta, idTipoPrenda).stream()
            .collect(Collectors.toMap(fila -> YearMonth.of(fila.anio(), fila.mes()), fila -> fila));

        List<VentasPorMesResponse> porMes = new ArrayList<>();
        long unidades = 0;
        double monto = 0;
        long pedidosSinDesglose = 0;
        for (YearMonth mes : mesesDelPeriodo(filtros)) {
            UnidadesMontoPorMes desglosado = desglosadoPorMes.get(mes);
            PedidosSinDesglosePorMes sinDesglose = sinDesglosePorMes.get(mes);

            long unidadesMes = desglosado != null ? valor(desglosado.unidades()) : 0;
            double montoMes = desglosado != null ? valor(desglosado.monto()) : 0;
            if (sinDesglose != null) {
                pedidosSinDesglose += valor(sinDesglose.pedidos());
                if (!filtraPorTipoPrenda) {
                    montoMes += valor(sinDesglose.montoReferencia());
                }
            }

            porMes.add(new VentasPorMesResponse(mes.toString(), unidadesMes, montoMes));
            unidades += unidadesMes;
            monto += montoMes;
        }

        boolean montoIncompleto = filtraPorTipoPrenda && pedidosSinDesglose > 0;
        long unidadesConPrecio = montoIncompleto
            ? unidades - reporteVentasRepository.unidadesSinDesglose(estados, desde, hasta, idTipoPrenda)
            : unidades;
        double precioPromedioUnidad = unidadesConPrecio > 0 ? monto / unidadesConPrecio : 0;

        long cantidadPedidos = reporteVentasRepository.contarPedidos(estados, desde, hasta, idTipoPrenda);

        return new ResumenVentas(
            unidades, monto, precioPromedioUnidad, cantidadPedidos, montoIncompleto, pedidosSinDesglose, porMes);
    }

    private static long valor(Long numero) {
        return numero != null ? numero : 0;
    }

    private static double valor(Double numero) {
        return numero != null ? numero : 0;
    }

    private record ResumenVentas(
        long unidades,
        double monto,
        double precioPromedioUnidad,
        long cantidadPedidos,
        boolean montoIncompleto,
        long pedidosSinDesglose,
        List<VentasPorMesResponse> porMes
    ) {
    }
}
