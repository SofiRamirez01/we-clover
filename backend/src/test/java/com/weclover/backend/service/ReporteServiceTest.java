package com.weclover.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.weclover.backend.dto.reportes.PedidosSinDesglosePorMes;
import com.weclover.backend.dto.reportes.ProduccionPorEstadoResponse;
import com.weclover.backend.dto.reportes.ProduccionPorMesResponse;
import com.weclover.backend.dto.reportes.ReporteProduccionResponse;
import com.weclover.backend.dto.reportes.UnidadesPorEstado;
import com.weclover.backend.dto.reportes.UnidadesPorIndiceMes;
import com.weclover.backend.dto.reportes.ReporteFiltros;
import com.weclover.backend.dto.reportes.ReporteVentasResponse;
import com.weclover.backend.dto.reportes.UnidadesMontoPorMes;
import com.weclover.backend.dto.reportes.UnidadesMontoPorTipoPrenda;
import com.weclover.backend.dto.reportes.VentasPorMesResponse;
import com.weclover.backend.entity.EstadoProduccion;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ForbiddenException;
import com.weclover.backend.repository.ReporteProduccionRepository;
import com.weclover.backend.repository.ReporteVentasRepository;

@ExtendWith(MockitoExtension.class)
class ReporteServiceTest {

    private static final Long ID_ADMIN = 1L;
    private static final LocalDate DESDE = LocalDate.of(2026, 7, 10);
    private static final LocalDate HASTA = LocalDate.of(2026, 10, 2);
    private static final Long ID_REMERA = 3L;

    @Mock private ReporteVentasRepository reporteVentasRepository;
    @Mock private ReporteProduccionRepository reporteProduccionRepository;
    @Mock private AutorizacionService autorizacionService;

    @InjectMocks
    private ReporteService service;

    /** Vendidos: 30 u. / $3000 en julio y 10 u. / $500 en septiembre (agosto y octubre vacíos). */
    private void stubVendidos(Long idTipoPrenda) {
        when(reporteVentasRepository.unidadesMontoPorMes(ReporteService.ESTADOS_VENDIDO, DESDE, HASTA, idTipoPrenda))
            .thenReturn(List.of(
                new UnidadesMontoPorMes(2026, 9, 10L, 500d),
                new UnidadesMontoPorMes(2026, 7, 30L, 3000d)));
        when(reporteVentasRepository.contarPedidos(ReporteService.ESTADOS_VENDIDO, DESDE, HASTA, idTipoPrenda))
            .thenReturn(3L);
        lenient().when(reporteVentasRepository.unidadesMontoPorTipoPrenda(
                ReporteService.ESTADOS_VENDIDO, DESDE, HASTA, idTipoPrenda))
            .thenReturn(List.of(new UnidadesMontoPorTipoPrenda(ID_REMERA, "Remera", 40L, 3500d)));
    }

    @Test
    void ventas_totalesYPrecioPromedio() {
        stubVendidos(null);

        ReporteVentasResponse respuesta = service.ventas(DESDE, HASTA, null, ID_ADMIN);

        assertThat(respuesta.unidadesTotales()).isEqualTo(40);
        assertThat(respuesta.montoTotal()).isEqualTo(3500d);
        assertThat(respuesta.precioPromedioUnidad()).isCloseTo(87.5, within(0.001));
        assertThat(respuesta.cantidadPedidos()).isEqualTo(3);
        assertThat(respuesta.montoIncompleto()).isFalse();
        assertThat(respuesta.pedidosSinDesglose()).isZero();
        assertThat(respuesta.porTipoPrenda()).singleElement()
            .satisfies(fila -> {
                assertThat(fila.tipoPrenda()).isEqualTo("Remera");
                assertThat(fila.unidades()).isEqualTo(40);
                assertThat(fila.monto()).isEqualTo(3500d);
            });
    }

    @Test
    void ventas_completaEnCeroLosMesesSinVentas_enOrden() {
        stubVendidos(null);

        ReporteVentasResponse respuesta = service.ventas(DESDE, HASTA, null, ID_ADMIN);

        assertThat(respuesta.porMes()).containsExactly(
            new VentasPorMesResponse("2026-07", 30, 3000d),
            new VentasPorMesResponse("2026-08", 0, 0d),
            new VentasPorMesResponse("2026-09", 10, 500d),
            new VentasPorMesResponse("2026-10", 0, 0d));
    }

    @Test
    void ventas_sinFiltro_sumaElMontoDeLosPedidosSinDesglose() {
        stubVendidos(null);
        when(reporteVentasRepository.pedidosSinDesglosePorMes(ReporteService.ESTADOS_VENDIDO, DESDE, HASTA, null))
            .thenReturn(List.of(new PedidosSinDesglosePorMes(2026, 8, 2L, 1000d)));

        ReporteVentasResponse respuesta = service.ventas(DESDE, HASTA, null, ID_ADMIN);

        assertThat(respuesta.montoTotal()).isEqualTo(4500d);
        assertThat(respuesta.porMes().get(1)).isEqualTo(new VentasPorMesResponse("2026-08", 0, 1000d));
        assertThat(respuesta.pedidosSinDesglose()).isEqualTo(2);
        assertThat(respuesta.montoIncompleto()).isFalse();
        // Viendo todas las prendas, todas las unidades tienen precio: no hace falta descontar.
        verify(reporteVentasRepository, never()).unidadesSinDesglose(any(), any(), any(), any());
    }

    @Test
    void ventas_conFiltroDeTipoPrenda_excluyeMontoSinDesgloseYAvisa() {
        stubVendidos(ID_REMERA);
        when(reporteVentasRepository.pedidosSinDesglosePorMes(ReporteService.ESTADOS_VENDIDO, DESDE, HASTA, ID_REMERA))
            .thenReturn(List.of(new PedidosSinDesglosePorMes(2026, 9, 1L, 1000d)));
        when(reporteVentasRepository.unidadesSinDesglose(ReporteService.ESTADOS_VENDIDO, DESDE, HASTA, ID_REMERA))
            .thenReturn(5L);

        ReporteVentasResponse respuesta = service.ventas(DESDE, HASTA, ID_REMERA, ID_ADMIN);

        assertThat(respuesta.idTipoPrenda()).isEqualTo(ID_REMERA);
        assertThat(respuesta.unidadesTotales()).isEqualTo(40);
        assertThat(respuesta.montoTotal()).isEqualTo(3500d);
        assertThat(respuesta.montoIncompleto()).isTrue();
        assertThat(respuesta.pedidosSinDesglose()).isEqualTo(1);
        // 3500 / (40 - 5 unidades sin precio)
        assertThat(respuesta.precioPromedioUnidad()).isCloseTo(100d, within(0.001));
    }

    @Test
    void ventas_presupuestadosVanEnBloqueAparte() {
        stubVendidos(null);
        when(reporteVentasRepository.unidadesMontoPorMes(ReporteService.ESTADOS_PRESUPUESTADO, DESDE, HASTA, null))
            .thenReturn(List.of(new UnidadesMontoPorMes(2026, 10, 12L, 600d)));
        when(reporteVentasRepository.contarPedidos(ReporteService.ESTADOS_PRESUPUESTADO, DESDE, HASTA, null))
            .thenReturn(2L);

        ReporteVentasResponse respuesta = service.ventas(DESDE, HASTA, null, ID_ADMIN);

        assertThat(respuesta.presupuestados().cantidadPedidos()).isEqualTo(2);
        assertThat(respuesta.presupuestados().unidades()).isEqualTo(12);
        assertThat(respuesta.presupuestados().monto()).isEqualTo(600d);
        // No se mezclan con lo vendido.
        assertThat(respuesta.unidadesTotales()).isEqualTo(40);
    }

    @Test
    void ventas_sinDatos_devuelveTodoEnCero() {
        ReporteVentasResponse respuesta = service.ventas(DESDE, HASTA, null, ID_ADMIN);

        assertThat(respuesta.unidadesTotales()).isZero();
        assertThat(respuesta.montoTotal()).isZero();
        assertThat(respuesta.precioPromedioUnidad()).isZero();
        assertThat(respuesta.porMes()).hasSize(4).allMatch(mes -> mes.unidades() == 0 && mes.monto() == 0);
        assertThat(respuesta.porTipoPrenda()).isEmpty();
    }

    @Test
    void resolverFiltros_desdePosteriorAHasta_rechaza() {
        assertThatThrownBy(() -> service.ventas(HASTA, DESDE, null, ID_ADMIN))
            .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(reporteVentasRepository);
    }

    @Test
    void resolverFiltros_porDefecto_ultimos12Meses() {
        ReporteFiltros filtros = service.resolverFiltros(null, null, null);

        assertThat(filtros.hasta()).isEqualTo(LocalDate.now());
        assertThat(filtros.desde()).isEqualTo(YearMonth.now().minusMonths(11).atDay(1));
        assertThat(ReporteService.mesesDelPeriodo(filtros)).hasSize(12);
    }

    @Test
    void ventas_sinRolAdministrativo_noConsulta() {
        doThrow(new ForbiddenException("no")).when(autorizacionService).verificarRolAdministrativo(eq(99L));

        assertThatThrownBy(() -> service.ventas(DESDE, HASTA, null, 99L)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(reporteVentasRepository);
    }

    // ---- Producción ----

    @Test
    void produccion_devuelveLos9EstadosEnOrden_conCerosIncluidos() {
        when(reporteProduccionRepository.unidadesPorEstado(ReporteService.ESTADOS_VENDIDO, null))
            .thenReturn(List.of(
                new UnidadesPorEstado(EstadoProduccion.ENTREGADO, 56L),
                new UnidadesPorEstado(EstadoProduccion.CONFECCION, 42L),
                new UnidadesPorEstado(EstadoProduccion.PENDIENTE, 23L)));

        ReporteProduccionResponse respuesta = service.produccion(DESDE, HASTA, null, ID_ADMIN);

        assertThat(respuesta.porEstado()).containsExactly(
            new ProduccionPorEstadoResponse(EstadoProduccion.PENDIENTE, 23),
            new ProduccionPorEstadoResponse(EstadoProduccion.CORTADO, 0),
            new ProduccionPorEstadoResponse(EstadoProduccion.ESTAMPADO, 0),
            new ProduccionPorEstadoResponse(EstadoProduccion.BORDADO, 0),
            new ProduccionPorEstadoResponse(EstadoProduccion.CONFECCION, 42),
            new ProduccionPorEstadoResponse(EstadoProduccion.APODO, 0),
            new ProduccionPorEstadoResponse(EstadoProduccion.OJAL, 0),
            new ProduccionPorEstadoResponse(EstadoProduccion.TERMINADO, 0),
            new ProduccionPorEstadoResponse(EstadoProduccion.ENTREGADO, 56));
    }

    @Test
    void produccion_unidadesEnPlanta_excluyeEntregado() {
        when(reporteProduccionRepository.unidadesPorEstado(ReporteService.ESTADOS_VENDIDO, null))
            .thenReturn(List.of(
                new UnidadesPorEstado(EstadoProduccion.ENTREGADO, 56L),
                new UnidadesPorEstado(EstadoProduccion.TERMINADO, 17L),
                new UnidadesPorEstado(EstadoProduccion.CONFECCION, 42L)));

        ReporteProduccionResponse respuesta = service.produccion(DESDE, HASTA, null, ID_ADMIN);

        assertThat(respuesta.unidadesEnPlanta()).isEqualTo(59);
    }

    @Test
    void produccion_terminadasPorMes_completaMesesEnCero_yConsultaElRangoCorrecto() {
        // Índice = meses desde el día 1 del mes de DESDE (julio): 0=jul, 2=sep.
        when(reporteProduccionRepository.terminadasPorMes(
                ReporteService.ESTADOS_VENDIDO,
                LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 7, 10, 0, 0),
                LocalDateTime.of(2026, 10, 3, 0, 0),
                null))
            .thenReturn(List.of(new UnidadesPorIndiceMes(2, 17L), new UnidadesPorIndiceMes(0, 5L)));

        ReporteProduccionResponse respuesta = service.produccion(DESDE, HASTA, null, ID_ADMIN);

        assertThat(respuesta.terminadasPorMes()).containsExactly(
            new ProduccionPorMesResponse("2026-07", 5),
            new ProduccionPorMesResponse("2026-08", 0),
            new ProduccionPorMesResponse("2026-09", 17),
            new ProduccionPorMesResponse("2026-10", 0));
    }

    @Test
    void produccion_filtroPorTipoPrenda_llegaALasDosConsultas() {
        when(reporteProduccionRepository.unidadesPorEstado(ReporteService.ESTADOS_VENDIDO, ID_REMERA))
            .thenReturn(List.of(new UnidadesPorEstado(EstadoProduccion.BORDADO, 23L)));

        ReporteProduccionResponse respuesta = service.produccion(DESDE, HASTA, ID_REMERA, ID_ADMIN);

        assertThat(respuesta.idTipoPrenda()).isEqualTo(ID_REMERA);
        assertThat(respuesta.unidadesEnPlanta()).isEqualTo(23);
        verify(reporteProduccionRepository).terminadasPorMes(
            eq(ReporteService.ESTADOS_VENDIDO), any(), any(), any(), eq(ID_REMERA));
    }

    @Test
    void produccion_sinRolAdministrativo_noConsulta() {
        doThrow(new ForbiddenException("no")).when(autorizacionService).verificarRolAdministrativo(eq(99L));

        assertThatThrownBy(() -> service.produccion(DESDE, HASTA, null, 99L)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(reporteProduccionRepository);
    }

    @Test
    void produccion_desdePosteriorAHasta_rechaza() {
        assertThatThrownBy(() -> service.produccion(HASTA, DESDE, null, ID_ADMIN))
            .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(reporteProduccionRepository);
    }
}
