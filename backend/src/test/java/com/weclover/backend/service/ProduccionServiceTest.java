package com.weclover.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.weclover.backend.dto.produccion.ProduccionPedidoResponse;
import com.weclover.backend.dto.tanda.EstadoTanda;
import com.weclover.backend.entity.Colegio;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.NotaPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Tanda;
import com.weclover.backend.entity.TipoPrenda;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.repository.NotaPedidoRepository;
import com.weclover.backend.repository.PedidoRepository;
import com.weclover.backend.repository.TandaRepository;

/** Grilla de Producción agrupada por tanda (paso 5 de Tandas). */
@ExtendWith(MockitoExtension.class)
class ProduccionServiceTest {

    private static final Long ID_ACTOR = 7L;

    @Mock private PedidoRepository pedidoRepository;
    @Mock private TandaRepository tandaRepository;
    @Mock private NotaPedidoRepository notaPedidoRepository;
    @Mock private EstadoPedidoService estadoPedidoService;
    @Mock private ProductoEtapaProduccionService productoEtapaProduccionService;
    @Mock private AutorizacionService autorizacionService;

    private ProduccionService service;

    private final List<Tanda> tandas = new ArrayList<>();
    private final List<Pedido> pedidos = new ArrayList<>();
    private final List<NotaPedido> notas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TandaService tandaService = new TandaService(tandaRepository, pedidoRepository, autorizacionService);
        service = new ProduccionService(pedidoRepository, notaPedidoRepository, estadoPedidoService,
            productoEtapaProduccionService, tandaService, autorizacionService);

        lenient().when(tandaRepository.findAllByOrderByOrdenAscIdAsc()).thenAnswer(inv -> tandas.stream()
            .sorted((a, b) -> Integer.compare(a.getOrden(), b.getOrden()))
            .toList());
        lenient().when(pedidoRepository.findByTandaIsNotNull()).thenAnswer(inv -> pedidos.stream()
            .filter(pedido -> pedido.getTanda() != null)
            .toList());
        lenient().when(pedidoRepository.findAll()).thenReturn(pedidos);
        lenient().when(pedidoRepository.findByEstadoActualIn(any())).thenAnswer(inv -> {
            List<EstadoPedido> estados = inv.getArgument(0);
            return pedidos.stream().filter(pedido -> estados.contains(pedido.getEstadoActual())).toList();
        });
        lenient().when(notaPedidoRepository.findByPedidoIdInOrderByFechaDescIdDesc(anyCollection())).thenReturn(notas);
        lenient().when(productoEtapaProduccionService.calcularEstadoVisual(any())).thenReturn("PENDIENTE");
    }

    private Tanda tanda(long id, String nombre, int orden) {
        Tanda tanda = Tanda.builder().id(id).nombre(nombre).orden(orden).build();
        tandas.add(tanda);
        return tanda;
    }

    private Pedido pedido(long id, EstadoPedido estado, boolean etapaCompletada, Tanda tanda) {
        Pedido pedido = TandaEstadoCalculadorTest.pedido(id, estado, etapaCompletada);
        pedido.setCodigoInterno("P" + id);
        pedido.setColegio(Colegio.builder().nombre("Colegio " + id).build());
        pedido.setTanda(tanda);
        pedido.getProductos().forEach(producto -> {
            producto.setTipoPrenda(TipoPrenda.builder().nombre("Remera").build());
            producto.setInsumosSecundarios(new ArrayList<>());
        });
        pedidos.add(pedido);
        return pedido;
    }

    @Test
    void ordenaPorTandaAbiertaLuegoCerradaYAlFinalSinTandaPorPuntajeSugerido() {
        Tanda b = tanda(2, "B", 20);
        Tanda a = tanda(1, "A", 10);
        Tanda cerrada = tanda(3, "C", 5);
        pedido(1, EstadoPedido.LISTO_PARA_PRODUCCION, false, null);
        pedido(2, EstadoPedido.LISTO_PARA_PRODUCCION, false, b);
        pedido(3, EstadoPedido.ENTREGADO, true, cerrada);
        pedido(4, EstadoPedido.EN_PRODUCCION, true, a);
        pedido(5, EstadoPedido.SENADO, false, null);
        lenient().when(estadoPedidoService.calcularPrioridadesAutomaticas()).thenReturn(Map.of(1L, 9, 5L, 2));

        List<ProduccionPedidoResponse> grilla = service.listar(null, null, null, null, ID_ACTOR);

        assertThat(grilla).extracting(ProduccionPedidoResponse::id).containsExactly(4L, 2L, 3L, 5L, 1L);
        assertThat(grilla.get(0).tanda().nombre()).isEqualTo("A");
        assertThat(grilla.get(0).tanda().posicion()).isEqualTo(1);
        assertThat(grilla.get(0).tanda().estado()).isEqualTo(EstadoTanda.EN_PRODUCCION);
        assertThat(grilla.get(1).tanda().posicion()).isEqualTo(2);
        assertThat(grilla.get(2).tanda().estado()).isEqualTo(EstadoTanda.CERRADA);
        assertThat(grilla.get(2).tanda().posicion()).isNull();
        assertThat(grilla.get(3).tanda()).isNull();
    }

    @Test
    void losPedidosDeUnaTandaAbiertaSeVenAunqueElFiltroDeEstadoLosExcluya() {
        Tanda a = tanda(1, "A", 10);
        Tanda cerrada = tanda(2, "B", 5);
        pedido(1, EstadoPedido.SENADO, false, a);
        pedido(2, EstadoPedido.SENADO, false, null);
        pedido(3, EstadoPedido.ENTREGADO, true, cerrada);
        pedido(4, EstadoPedido.LISTO_PARA_PRODUCCION, false, null);

        List<ProduccionPedidoResponse> grilla = service.listar(
            List.of(EstadoPedido.LISTO_PARA_PRODUCCION), null, null, null, ID_ACTOR);

        // Entra el SENADO que está en una tanda abierta; no el SENADO suelto ni el de la cerrada.
        assertThat(grilla).extracting(ProduccionPedidoResponse::id).containsExactly(1L, 4L);
    }

    @Test
    void traeLaUltimaNotaLaCantidadYLaUbicacion() {
        Pedido pedido = pedido(1, EstadoPedido.LISTO_PARA_PRODUCCION, false, null);
        pedido.setUbicacionActual("con Adrián");
        Usuario autor = Usuario.builder().nombre("Adrián").build();
        notas.add(NotaPedido.builder().id(2L).pedido(pedido).texto("llega tela el lunes").autor(autor)
            .fecha(LocalDateTime.now()).build());
        notas.add(NotaPedido.builder().id(1L).pedido(pedido).texto("falta bandera").autor(autor)
            .fecha(LocalDateTime.now().minusDays(1)).build());

        ProduccionPedidoResponse respuesta = service.listar(null, null, null, null, ID_ACTOR).get(0);

        assertThat(respuesta.ultimaNota().texto()).isEqualTo("llega tela el lunes");
        assertThat(respuesta.cantidadNotas()).isEqualTo(2);
        assertThat(respuesta.ubicacionActual()).isEqualTo("con Adrián");
    }
}
