package com.weclover.backend.service;

import static com.weclover.backend.service.InsumosDePrueba.CIERRE;
import static com.weclover.backend.service.InsumosDePrueba.FRIZA;
import static com.weclover.backend.service.InsumosDePrueba.JERSEY;
import static com.weclover.backend.service.InsumosDePrueba.RIBB;
import static com.weclover.backend.service.InsumosDePrueba.TIPO_PUNOS_CINTURA;
import static com.weclover.backend.service.InsumosDePrueba.agregar;
import static com.weclover.backend.service.InsumosDePrueba.capucha;
import static com.weclover.backend.service.InsumosDePrueba.cierre;
import static com.weclover.backend.service.InsumosDePrueba.color;
import static com.weclover.backend.service.InsumosDePrueba.estampado;
import static com.weclover.backend.service.InsumosDePrueba.libre;
import static com.weclover.backend.service.InsumosDePrueba.pieza;
import static com.weclover.backend.service.InsumosDePrueba.pin;
import static com.weclover.backend.service.InsumosDePrueba.productoConDisenoCargado;
import static com.weclover.backend.service.InsumosDePrueba.punosYCintura;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.weclover.backend.entity.EstadoPlanificacionCompra;
import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.PlanificacionCompra;
import com.weclover.backend.entity.PlanificacionCompraDetalle;
import com.weclover.backend.entity.PlanificacionCompraProductoBorrador;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.UnidadMedida;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.repository.ArticuloProveedorRepository;
import com.weclover.backend.repository.ArticuloStockRepository;
import com.weclover.backend.repository.PlanificacionCompraDetalleRepository;
import com.weclover.backend.repository.PlanificacionCompraRepository;
import com.weclover.backend.repository.ProductoRepository;
import com.weclover.backend.repository.StockRepository;
import com.weclover.backend.repository.UsuarioRepository;

/**
 * Regresión del cálculo de compras al confirmar una planificación: una fila por (tela, color)
 * de cada producto, sumando tela de cuerpo e insumos secundarios, en la unidad de compra. La
 * elegibilidad se evalúa con el ProductoDisenoValidador real (ProductoService solo delega).
 */
@ExtendWith(MockitoExtension.class)
class PlanificacionCompraServiceCalculoTest {

    private static final Long ID_ACTOR = 7L;
    private static final Long ID_PLANIFICACION = 30L;

    private static final PaletaColores ROJO_FRIZA = color(1L, "Rojo", FRIZA);
    private static final PaletaColores ROJO_JERSEY = color(2L, "Rojo", JERSEY);
    private static final PaletaColores ROJO_RIBB = color(3L, "Rojo", RIBB);
    private static final PaletaColores BLANCO_RIBB = color(4L, "Blanco", RIBB);
    private static final PaletaColores ROJO_CIERRE = color(5L, "Rojo", CIERRE);

    @Mock private PlanificacionCompraRepository planificacionCompraRepository;
    @Mock private PlanificacionCompraDetalleRepository planificacionCompraDetalleRepository;
    @Mock private ProductoRepository productoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ArticuloProveedorRepository articuloProveedorRepository;
    @Mock private ArticuloStockRepository articuloStockRepository;
    @Mock private StockRepository stockRepository;
    @Mock private ProductoService productoService;
    @Mock private TandaService tandaService;
    @Mock private AutorizacionService autorizacionService;

    @InjectMocks
    private PlanificacionCompraService service;

    private PlanificacionCompra planificacion;

    @BeforeEach
    void setUp() {
        planificacion = PlanificacionCompra.builder()
            .id(ID_PLANIFICACION)
            .estado(EstadoPlanificacionCompra.BORRADOR)
            .nombre("Semana 1")
            .fechaCreacion(LocalDateTime.of(2026, 10, 1, 9, 0))
            .creadoPor(Usuario.builder().id(ID_ACTOR).nombre("Admin").build())
            .productosBorrador(new ArrayList<>())
            .detalles(new ArrayList<>())
            .build();
        when(planificacionCompraRepository.findById(ID_PLANIFICACION)).thenReturn(Optional.of(planificacion));
        lenient().when(planificacionCompraRepository.save(any(PlanificacionCompra.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productoService.motivoDisenoIncompleto(any(Producto.class)))
            .thenAnswer(inv -> ProductoDisenoValidador.motivoDisenoIncompleto(inv.getArgument(0)));
    }

    private Producto enBorrador(Producto producto) {
        producto.setPedido(Pedido.builder().id(1L).fechaEstimadaEntrega(LocalDate.of(2026, 11, 15)).build());
        planificacion.getProductosBorrador().add(PlanificacionCompraProductoBorrador.builder()
            .planificacionCompra(planificacion).producto(producto).build());
        return producto;
    }

    private List<PlanificacionCompraDetalle> confirmar() {
        service.confirmar(ID_PLANIFICACION, ID_ACTOR);
        assertThat(planificacion.getEstado()).isEqualTo(EstadoPlanificacionCompra.CONFIRMADA);
        return planificacion.getDetalles();
    }

    private static float cantidadDe(List<PlanificacionCompraDetalle> detalles, PaletaColores color) {
        return detalles.stream().filter(d -> d.getPaletaColor() == color).findFirst().orElseThrow().getCantidad();
    }

    @Test
    void campera_calculaCuerpoEInsumosEnSuUnidadDeCompra() {
        Producto campera = enBorrador(productoConDisenoCargado(1L, "Campera", 10, ROJO_FRIZA, 600));
        agregar(campera, cierre(ROJO_CIERRE), capucha(ROJO_JERSEY, 70f), punosYCintura(ROJO_RIBB, 60f));

        List<PlanificacionCompraDetalle> detalles = confirmar();

        assertThat(detalles)
            .extracting(d -> d.getTipoTela().getCodigo(), PlanificacionCompraDetalle::getPaletaColor,
                PlanificacionCompraDetalle::getUnidadMedida)
            .containsExactly(
                tuple("FRIZA", ROJO_FRIZA, UnidadMedida.KG),
                tuple("CIERRE", ROJO_CIERRE, UnidadMedida.UNIDAD),
                tuple("JERSEY", ROJO_JERSEY, UnidadMedida.KG),
                tuple("RIBB", ROJO_RIBB, UnidadMedida.KG));
        assertThat(cantidadDe(detalles, ROJO_FRIZA)).isCloseTo(6.0f, within(0.0001f));
        assertThat(cantidadDe(detalles, ROJO_CIERRE)).isCloseTo(10f, within(0.0001f));
        assertThat(cantidadDe(detalles, ROJO_JERSEY)).isCloseTo(0.7f, within(0.0001f));
        assertThat(cantidadDe(detalles, ROJO_RIBB)).isCloseTo(0.6f, within(0.0001f));
        assertThat(detalles).allSatisfy(d -> assertThat(d.getProducto()).isSameAs(campera));
    }

    @Test
    void dosFilasDelMismoInsumoConDistintoColor_generanUnDetallePorColor() {
        Producto buzo = enBorrador(productoConDisenoCargado(1L, "Buzo", 10, ROJO_FRIZA, 600));
        agregar(buzo, capucha(ROJO_JERSEY, 70f), punosYCintura(ROJO_RIBB, 50f), punosYCintura(BLANCO_RIBB, 10f));

        List<PlanificacionCompraDetalle> detalles = confirmar();

        assertThat(detalles).hasSize(4);
        assertThat(cantidadDe(detalles, ROJO_RIBB)).isCloseTo(0.5f, within(0.0001f));
        assertThat(cantidadDe(detalles, BLANCO_RIBB)).isCloseTo(0.1f, within(0.0001f));
    }

    @Test
    void insumoDeLaMismaTelaYColorQueElCuerpo_seSumaEnElMismoDetalle() {
        Producto remera = enBorrador(productoConDisenoCargado(1L, "Remera", 10, ROJO_FRIZA, 300, 300));
        agregar(remera, libre("Bolsillo", FRIZA, ROJO_FRIZA, 20f));

        List<PlanificacionCompraDetalle> detalles = confirmar();

        assertThat(detalles).singleElement().satisfies(detalle -> {
            assertThat(detalle.getTipoTela()).isSameAs(FRIZA);
            assertThat(detalle.getPaletaColor()).isSameAs(ROJO_FRIZA);
            assertThat(detalle.getCantidad()).isCloseTo(6.2f, within(0.0001f));
            assertThat(detalle.getUnidadMedida()).isEqualTo(UnidadMedida.KG);
        });
    }

    @Test
    void unaMarcaSinMaterial_noGeneraDetalle() {
        Producto remera = enBorrador(productoConDisenoCargado(1L, "Remera", 10, ROJO_FRIZA, 200));
        agregar(remera, estampado());

        List<PlanificacionCompraDetalle> detalles = confirmar();

        assertThat(detalles).singleElement()
            .satisfies(detalle -> assertThat(detalle.getCantidad()).isCloseTo(2.0f, within(0.0001f)));
    }

    @Test
    void variasFilasDelMismoInsumo_noImpidenConfirmar() {
        Producto buzo = enBorrador(productoConDisenoCargado(1L, "Buzo", 10, ROJO_FRIZA, 600));
        agregar(buzo, capucha(ROJO_JERSEY, 70f), punosYCintura(ROJO_RIBB, 50f), punosYCintura(BLANCO_RIBB, 10f));

        assertThat(confirmar()).isNotEmpty();
    }

    /** Pines de insumo sin asignar: el producto sigue siendo elegible y el cálculo no cambia. */
    @Test
    void pinesDeInsumoSinAsignar_noImpidenConfirmarNiCambianElCalculo() {
        Producto buzo = enBorrador(productoConDisenoCargado(1L, "Buzo", 10, ROJO_FRIZA, 600));
        agregar(buzo, capucha(ROJO_JERSEY, 70f), punosYCintura(ROJO_RIBB, 50f), punosYCintura(BLANCO_RIBB, 10f));
        pin(900L, buzo, 0, pieza(50L, "Puño", TIPO_PUNOS_CINTURA), null);
        pin(901L, buzo, 0, pieza(51L, "Cintura", TIPO_PUNOS_CINTURA), null);
        assertThat(AsignacionInsumoPieza.pinesSinAsignar(buzo)).hasSize(2);

        List<PlanificacionCompraDetalle> detalles = confirmar();

        assertThat(detalles).hasSize(4);
        assertThat(cantidadDe(detalles, ROJO_FRIZA)).isCloseTo(6.0f, within(0.0001f));
        assertThat(cantidadDe(detalles, ROJO_RIBB)).isCloseTo(0.5f, within(0.0001f));
        assertThat(cantidadDe(detalles, BLANCO_RIBB)).isCloseTo(0.1f, within(0.0001f));
    }

    @Test
    void productoConInsumoExigidoFaltante_noSePuedeConfirmar() {
        Producto buzo = enBorrador(productoConDisenoCargado(1L, "Buzo", 10, ROJO_FRIZA, 600));
        agregar(buzo, punosYCintura(ROJO_RIBB, 60f));

        assertThatThrownBy(() -> service.confirmar(ID_PLANIFICACION, ID_ACTOR))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("El producto #1 no tiene el diseño completo: Faltan insumos secundarios: Capucha");
        assertThat(planificacion.getEstado()).isEqualTo(EstadoPlanificacionCompra.BORRADOR);
        assertThat(planificacion.getDetalles()).isEmpty();
    }
}
