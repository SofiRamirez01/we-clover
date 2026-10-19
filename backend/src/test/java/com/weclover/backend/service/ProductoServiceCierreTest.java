package com.weclover.backend.service;

import static com.weclover.backend.service.InsumosDePrueba.CIERRE;
import static com.weclover.backend.service.InsumosDePrueba.JERSEY;
import static com.weclover.backend.service.InsumosDePrueba.RIBB;
import static com.weclover.backend.service.InsumosDePrueba.TIPO_CIERRE;
import static com.weclover.backend.service.InsumosDePrueba.agregar;
import static com.weclover.backend.service.InsumosDePrueba.capucha;
import static com.weclover.backend.service.InsumosDePrueba.cierre;
import static com.weclover.backend.service.InsumosDePrueba.color;
import static com.weclover.backend.service.InsumosDePrueba.producto;
import static com.weclover.backend.service.InsumosDePrueba.punosYCintura;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.weclover.backend.dto.producto.ActualizarColorCierreRequest;
import com.weclover.backend.dto.producto.ProductoResponse;
import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoInsumoSecundario;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.mapper.ProductoMapper;
import com.weclover.backend.repository.PaletaColoresRepository;
import com.weclover.backend.repository.PatronCorteRepository;
import com.weclover.backend.repository.ProductoRepository;
import com.weclover.backend.repository.TipoInsumoRepository;
import com.weclover.backend.repository.TipoTelaRepository;

/**
 * Regresión del cierre de una Campera: el endpoint dedicado (actualizarColorCierre) y los
 * campos idColorCierre/nombreColorCierre/hexColorCierre de la respuesta, que se derivan del
 * insumo Cierre del producto.
 */
@ExtendWith(MockitoExtension.class)
class ProductoServiceCierreTest {

    private static final Long ID_ACTOR = 7L;
    private static final Long ID_PRODUCTO = 10L;

    private static final PaletaColores NEGRO_CIERRE = color(50L, "Negro", CIERRE);
    private static final PaletaColores AZUL_CIERRE = color(51L, "Azul", CIERRE);
    private static final PaletaColores NEGRO_JERSEY = color(52L, "Negro", JERSEY);
    private static final PaletaColores NEGRO_RIBB = color(53L, "Negro", RIBB);

    @Mock private ProductoRepository productoRepository;
    @Mock private PaletaColoresRepository paletaColoresRepository;
    @Mock private TipoTelaRepository tipoTelaRepository;
    @Mock private TipoInsumoRepository tipoInsumoRepository;
    @Mock private PatronCorteRepository patronCorteRepository;
    @Mock private ProductoMapper productoMapper;
    @Mock private AutorizacionService autorizacionService;
    @Mock private AlmacenamientoImagenService almacenamientoImagenService;
    @Mock private EstadoPedidoService estadoPedidoService;
    @Mock private ProductoEtapaProduccionService productoEtapaProduccionService;

    @InjectMocks
    private ProductoService service;

    @BeforeEach
    void setUp() {
        lenient().when(tipoTelaRepository.findByCodigo("CIERRE")).thenReturn(Optional.of(CIERRE));
        lenient().when(tipoInsumoRepository.findByCodigo("CIERRE")).thenReturn(Optional.of(TIPO_CIERRE));
        lenient().when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(productoMapper.toResponse(any(Producto.class))).thenAnswer(inv -> respuestaBase(inv.getArgument(0)));
        for (PaletaColores color : List.of(NEGRO_CIERRE, AZUL_CIERRE, NEGRO_JERSEY)) {
            lenient().when(paletaColoresRepository.findById(color.getId())).thenReturn(Optional.of(color));
        }
    }

    /** Lo que devolvería el mapper real: todo menos los tres campos de cierre, que completa el service. */
    private static ProductoResponse respuestaBase(Producto producto) {
        return new ProductoResponse(producto.getId(), null, producto.getTipoPrenda().getNombre(), null, List.of(), null,
            null, null, null, producto.getCantidadTotal(), 0f, 0f, null, null, null, null, null, List.of(), List.of());
    }

    private Producto productoGuardado(String tipoPrenda) {
        Producto producto = producto(ID_PRODUCTO, tipoPrenda, 10);
        producto.setPedido(Pedido.builder().id(1L).build());
        lenient().when(productoRepository.findById(ID_PRODUCTO)).thenReturn(Optional.of(producto));
        return producto;
    }

    private static List<ProductoInsumoSecundario> filasDeCierre(Producto producto) {
        return producto.getInsumosSecundarios().stream()
            .filter(insumo -> insumo.getTipoTela() != null && "CIERRE".equals(insumo.getTipoTela().getCodigo()))
            .toList();
    }

    @Test
    void actualizarColorCierre_enCamperaSinCierre_creaUnaFilaPorUnidad() {
        Producto campera = productoGuardado("Campera");

        ProductoResponse respuesta = service.actualizarColorCierre(
            ID_PRODUCTO, new ActualizarColorCierreRequest(NEGRO_CIERRE.getId()), ID_ACTOR);

        assertThat(filasDeCierre(campera)).singleElement().satisfies(fila -> {
            assertThat(fila.getDescripcion()).isEqualTo("Cierre");
            assertThat(fila.getColor()).isSameAs(NEGRO_CIERRE);
            assertThat(fila.getCantidad()).isEqualTo(1f);
            assertThat(fila.getProducto()).isSameAs(campera);
        });
        assertThat(respuesta.idColorCierre()).isEqualTo(NEGRO_CIERRE.getId());
        assertThat(respuesta.nombreColorCierre()).isEqualTo("Negro");
        assertThat(respuesta.hexColorCierre()).isEqualTo(NEGRO_CIERRE.getHex());
        verify(estadoPedidoService).recalcularEstadoPedido(1L);
    }

    @Test
    void actualizarColorCierre_conCierreExistente_loReemplazaSinDuplicarNiTocarElResto() {
        Producto campera = productoGuardado("Campera");
        agregar(campera, capucha(NEGRO_JERSEY, 70f), cierre(NEGRO_CIERRE), punosYCintura(NEGRO_RIBB, 60f));

        ProductoResponse respuesta = service.actualizarColorCierre(
            ID_PRODUCTO, new ActualizarColorCierreRequest(AZUL_CIERRE.getId()), ID_ACTOR);

        assertThat(campera.getInsumosSecundarios()).hasSize(3);
        assertThat(filasDeCierre(campera)).singleElement()
            .satisfies(fila -> assertThat(fila.getColor()).isSameAs(AZUL_CIERRE));
        assertThat(respuesta.nombreColorCierre()).isEqualTo("Azul");
    }

    @Test
    void actualizarColorCierre_enPrendaQueNoEsCampera_seRechaza() {
        Producto buzo = productoGuardado("Buzo");

        assertThatThrownBy(() -> service.actualizarColorCierre(
                ID_PRODUCTO, new ActualizarColorCierreRequest(NEGRO_CIERRE.getId()), ID_ACTOR))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("Esta prenda no tiene cierre");
        assertThat(buzo.getInsumosSecundarios()).isEmpty();
    }

    @Test
    void actualizarColorCierre_conUnColorQueNoEsDeCierre_seRechaza() {
        Producto campera = productoGuardado("Campera");

        assertThatThrownBy(() -> service.actualizarColorCierre(
                ID_PRODUCTO, new ActualizarColorCierreRequest(NEGRO_JERSEY.getId()), ID_ACTOR))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("El color elegido no es un color de cierre");
        assertThat(campera.getInsumosSecundarios()).isEmpty();
    }

    @Test
    void construirRespuesta_sinCierre_dejaLosTresCamposEnNull() {
        Producto campera = productoGuardado("Campera");
        agregar(campera, capucha(NEGRO_JERSEY, 70f), punosYCintura(NEGRO_RIBB, 60f));

        ProductoResponse respuesta = service.construirRespuesta(campera);

        assertThat(respuesta.idColorCierre()).isNull();
        assertThat(respuesta.nombreColorCierre()).isNull();
        assertThat(respuesta.hexColorCierre()).isNull();
    }

    @Test
    void construirRespuesta_conCierre_tomaElColorDeEsaFilaYNoDeOtroInsumo() {
        Producto campera = productoGuardado("Campera");
        agregar(campera, capucha(NEGRO_JERSEY, 70f), cierre(AZUL_CIERRE));

        ProductoResponse respuesta = service.construirRespuesta(campera);

        assertThat(respuesta.idColorCierre()).isEqualTo(AZUL_CIERRE.getId());
        assertThat(respuesta.nombreColorCierre()).isEqualTo("Azul");
        assertThat(respuesta.hexColorCierre()).isEqualTo(AZUL_CIERRE.getHex());
    }
}
