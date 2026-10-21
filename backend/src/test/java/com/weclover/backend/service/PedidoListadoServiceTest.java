package com.weclover.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.weclover.backend.dto.pedido.CampoOrdenPedidos;
import com.weclover.backend.dto.pedido.ContadorEstadoPedidoResponse;
import com.weclover.backend.dto.pedido.FiltrosListadoPedidos;
import com.weclover.backend.dto.pedido.ListadoPedidosResponse;
import com.weclover.backend.dto.pedido.OpcionMolderiaResponse;
import com.weclover.backend.dto.pedido.OpcionesFiltroPedidosResponse;
import com.weclover.backend.dto.pedido.PedidoResponse;
import com.weclover.backend.dto.pedido.PedidosPorEstado;
import com.weclover.backend.dto.pedido.UnidadesPorEstadoYTipoPrenda;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.OrigenPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Rol;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ForbiddenException;
import com.weclover.backend.repository.OrdenListadoPedidos;
import com.weclover.backend.repository.PedidoRepository;

@ExtendWith(MockitoExtension.class)
class PedidoListadoServiceTest {

    private static final Long ID_ADMIN = 1L;
    private static final Long ID_VENDEDOR = 7L;

    @Mock private PedidoRepository pedidoRepository;
    @Mock private PedidoService pedidoService;
    @Mock private AutorizacionService autorizacionService;

    @InjectMocks
    private PedidoListadoService service;

    private static Usuario usuario(Long id, String rol) {
        return Usuario.builder().id(id).nombre("Usuario " + id).rol(Rol.builder().nombre(rol).build()).build();
    }

    private static FiltrosListadoPedidos sinFiltros() {
        return new FiltrosListadoPedidos(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private void actorEs(Long id, String rol) {
        when(autorizacionService.obtenerActorInterno(id)).thenReturn(usuario(id, rol));
    }

    /** Cualquier llamada al listado devuelve estos pedidos (total = cantidad de pedidos + extra). */
    private void listadoDevuelve(List<Pedido> pedidos, long total, Pageable pageable) {
        when(pedidoRepository.buscarListado(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(pedidos, pageable, total));
    }

    @Test
    void administrativoSinFiltrosVeTodoConLosValoresPorDefecto() {
        actorEs(ID_ADMIN, "ROLE_ADMINISTRATIVO");
        List<Pedido> pedidos = List.of(Pedido.builder().id(10L).build(), Pedido.builder().id(11L).build());
        listadoDevuelve(pedidos, 120, PageRequest.of(0, 50));
        PedidoResponse respuesta = org.mockito.Mockito.mock(PedidoResponse.class);
        when(pedidoService.construirRespuestas(pedidos)).thenReturn(List.of(respuesta, respuesta));

        ListadoPedidosResponse listado = service.listar(sinFiltros(), ID_ADMIN);

        verify(pedidoRepository).buscarListado(
            isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
            eq(false), eq(PedidoListadoService.ESTADOS_CERRADOS), isNull(), isNull(), isNull(),
            eq(PageRequest.of(0, PedidoListadoService.TAMANIO_POR_DEFECTO, OrdenListadoPedidos.de(null, null))));
        assertThat(listado.pedidos()).hasSize(2);
        assertThat(listado.total()).isEqualTo(120);
        assertThat(listado.pagina()).isZero();
        assertThat(listado.tamanio()).isEqualTo(50);
        assertThat(listado.hayMas()).isTrue();
    }

    @Test
    void unVendedorSoloVeSusPropiosPedidosEnElListadoYLosContadores() {
        actorEs(ID_VENDEDOR, "ROLE_VENDEDOR");
        listadoDevuelve(List.of(), 0, PageRequest.of(0, 50));

        ListadoPedidosResponse listado = service.listar(sinFiltros(), ID_VENDEDOR);

        verify(pedidoRepository).buscarListado(
            isNull(), isNull(), isNull(), eq(ID_VENDEDOR), isNull(), isNull(),
            eq(false), any(), isNull(), isNull(), isNull(), any());
        verify(pedidoRepository).contarPorEstado(null, null, null, ID_VENDEDOR);
        verify(pedidoRepository).sumarUnidadesPorEstadoYTipoPrenda(null, null, null, ID_VENDEDOR);
        assertThat(listado.hayMas()).isFalse();
    }

    @Test
    void sinUsuarioInternoNoSeConsultaNada() {
        when(autorizacionService.obtenerActorInterno(99L))
            .thenThrow(new ForbiddenException("Esta acción es solo para usuarios internos"));

        assertThatThrownBy(() -> service.listar(sinFiltros(), 99L)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.opcionesFiltro(99L)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(pedidoRepository, pedidoService);
    }

    @Test
    void pasaTodosLosFiltrosAlRepositorioYLosContadoresSoloUsanPromocionFechasYVendedor() {
        actorEs(ID_ADMIN, "ROLE_ADMINISTRATIVO");
        LocalDate desde = LocalDate.of(2026, 3, 1);
        LocalDate hasta = LocalDate.of(2026, 9, 30);
        listadoDevuelve(List.of(), 0, PageRequest.of(2, 20));
        FiltrosListadoPedidos filtros = new FiltrosListadoPedidos(
            "  Morón ", 2026, EstadoPedido.SENADO, true, 4L, 2L, desde, hasta, 2, 20,
            CampoOrdenPedidos.TOTAL, Sort.Direction.DESC, OrigenPedido.KOMMO);

        service.listar(filtros, ID_ADMIN);

        verify(pedidoRepository).buscarListado(
            eq("2026-%"), eq(desde), eq(hasta), isNull(), eq("%Morón%"), eq(EstadoPedido.SENADO),
            eq(true), eq(List.of(EstadoPedido.ENTREGADO, EstadoPedido.CANCELADO)), eq(4L), eq(2L),
            eq(OrigenPedido.KOMMO), eq(PageRequest.of(2, 20, OrdenListadoPedidos.de(CampoOrdenPedidos.TOTAL, Sort.Direction.DESC))));
        verify(pedidoRepository).contarPorEstado("2026-%", desde, hasta, null);
        verify(pedidoRepository).sumarUnidadesPorEstadoYTipoPrenda("2026-%", desde, hasta, null);
    }

    @Test
    void losContadoresTraenTodosLosEstadosConCeroYLasUnidadesPorTipoDePrenda() {
        actorEs(ID_ADMIN, "ROLE_ADMINISTRATIVO");
        listadoDevuelve(List.of(), 0, PageRequest.of(0, 50));
        when(pedidoRepository.contarPorEstado(any(), any(), any(), any())).thenReturn(List.of(
            new PedidosPorEstado(EstadoPedido.SENADO, 3L),
            new PedidosPorEstado(EstadoPedido.ENTREGADO, 1L)));
        when(pedidoRepository.sumarUnidadesPorEstadoYTipoPrenda(any(), any(), any(), any())).thenReturn(List.of(
            new UnidadesPorEstadoYTipoPrenda(EstadoPedido.SENADO, "Buzo", 60L),
            new UnidadesPorEstadoYTipoPrenda(EstadoPedido.SENADO, "Campera", 25L),
            new UnidadesPorEstadoYTipoPrenda(EstadoPedido.ENTREGADO, "Remera", 30L)));

        List<ContadorEstadoPedidoResponse> contadores = service.listar(sinFiltros(), ID_ADMIN).contadores();

        assertThat(contadores).extracting(ContadorEstadoPedidoResponse::estado)
            .containsExactly(EstadoPedido.values());
        assertThat(contadores.get(EstadoPedido.SENADO.ordinal()))
            .isEqualTo(new ContadorEstadoPedidoResponse(EstadoPedido.SENADO, 3, Map.of("Buzo", 60L, "Campera", 25L)));
        assertThat(contadores.get(EstadoPedido.ENTREGADO.ordinal()))
            .isEqualTo(new ContadorEstadoPedidoResponse(EstadoPedido.ENTREGADO, 1, Map.of("Remera", 30L)));
        assertThat(contadores.get(EstadoPedido.CANCELADO.ordinal()))
            .isEqualTo(new ContadorEstadoPedidoResponse(EstadoPedido.CANCELADO, 0, Map.of()));
    }

    @Test
    void rechazaFiltrosInvalidosSinConsultarLaBase() {
        actorEs(ID_ADMIN, "ROLE_ADMINISTRATIVO");
        List<FiltrosListadoPedidos> invalidos = List.of(
            new FiltrosListadoPedidos(null, 226, null, null, null, null, null, null, null, null, null, null, null),
            new FiltrosListadoPedidos(null, null, null, null, 4L, null, null, null, null, null, null, null, null),
            new FiltrosListadoPedidos(null, null, null, null, null, 2L, null, null, null, null, null, null, null),
            new FiltrosListadoPedidos(null, null, null, null, null, null,
                LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 1), null, null, null, null, null),
            new FiltrosListadoPedidos(null, null, null, null, null, null, null, null, -1, null, null, null, null),
            new FiltrosListadoPedidos(null, null, null, null, null, null, null, null, null, 0, null, null, null),
            new FiltrosListadoPedidos(null, null, null, null, null, null, null, null, null,
                PedidoListadoService.TAMANIO_MAXIMO + 1, null, null, null));

        for (FiltrosListadoPedidos filtros : invalidos) {
            assertThatThrownBy(() -> service.listar(filtros, ID_ADMIN))
                .as(filtros.toString())
                .isInstanceOf(BusinessRuleException.class);
        }
        verifyNoInteractions(pedidoRepository, pedidoService);
    }

    @Test
    void sinColumnaElegidaOrdenaDelMasNuevoAlMasViejo() {
        Sort orden = OrdenListadoPedidos.de(null, null);

        assertThat(orden).containsExactly(
            Sort.Order.desc("fechaCreacion"),
            Sort.Order.desc("id"));
    }

    @Test
    void ordenPorColumnaConDesempatePorIdEnLaMismaDireccion() {
        Sort ficha = OrdenListadoPedidos.de(CampoOrdenPedidos.FICHA, Sort.Direction.DESC);

        assertThat(ficha).extracting(Sort.Order::getProperty).containsExactly(
            "SUBSTRING(p.codigoInterno, 1, 4)", "LENGTH(p.codigoInterno)", "p.codigoInterno", "id");
        assertThat(ficha).allMatch(Sort.Order::isDescending);
        // Sin dirección, de menor a mayor.
        assertThat(OrdenListadoPedidos.de(CampoOrdenPedidos.COLEGIO, null)).allMatch(Sort.Order::isAscending);
        // Todas las columnas tienen su expresión.
        for (CampoOrdenPedidos campo : CampoOrdenPedidos.values()) {
            assertThat(OrdenListadoPedidos.de(campo, Sort.Direction.ASC)).as(campo.name()).hasSizeGreaterThan(1);
        }
    }

    @Test
    void laBusquedaEscapaLosComodinesDelLike() {
        assertThat(PedidoListadoService.patronBusqueda("50%_a!")).isEqualTo("%50!%!_a!!%");
        assertThat(PedidoListadoService.patronBusqueda("   ")).isNull();
        assertThat(PedidoListadoService.patronBusqueda(null)).isNull();
        assertThat(PedidoListadoService.prefijoPromocion(2027)).isEqualTo("2027-%");
        assertThat(PedidoListadoService.prefijoPromocion(null)).isNull();
    }

    @Test
    void opcionesDeFiltroOrdenadasYSoloDeLosPedidosDelVendedor() {
        actorEs(ID_VENDEDOR, "ROLE_VENDEDOR");
        when(pedidoRepository.listarPrefijosPromocion(ID_VENDEDOR)).thenReturn(List.of("2026", "2027", "2025", "abcd"));
        OpcionMolderiaResponse clasicaRemera = new OpcionMolderiaResponse(9L, 1, "Clásica", 3L, "Remera");
        OpcionMolderiaResponse lateralBuzo = new OpcionMolderiaResponse(4L, 4, "Bicolor Lateral", 1L, "Buzo");
        OpcionMolderiaResponse clasicaBuzo = new OpcionMolderiaResponse(1L, 1, "Clásica", 1L, "Buzo");
        when(pedidoRepository.listarMolderiasUsadas(ID_VENDEDOR))
            .thenReturn(List.of(clasicaRemera, lateralBuzo, clasicaBuzo));

        OpcionesFiltroPedidosResponse opciones = service.opcionesFiltro(ID_VENDEDOR);

        assertThat(opciones.promociones()).containsExactly(2027, 2026, 2025);
        assertThat(opciones.molderias()).containsExactly(clasicaBuzo, lateralBuzo, clasicaRemera);
    }
}
