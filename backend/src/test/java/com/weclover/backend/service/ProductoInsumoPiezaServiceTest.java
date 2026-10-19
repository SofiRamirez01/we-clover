package com.weclover.backend.service;

import static com.weclover.backend.service.InsumosDePrueba.FRIZA;
import static com.weclover.backend.service.InsumosDePrueba.JERSEY;
import static com.weclover.backend.service.InsumosDePrueba.RIBB;
import static com.weclover.backend.service.InsumosDePrueba.TIPO_CAPUCHA;
import static com.weclover.backend.service.InsumosDePrueba.TIPO_PUNOS_CINTURA;
import static com.weclover.backend.service.InsumosDePrueba.agregar;
import static com.weclover.backend.service.InsumosDePrueba.asignar;
import static com.weclover.backend.service.InsumosDePrueba.capucha;
import static com.weclover.backend.service.InsumosDePrueba.color;
import static com.weclover.backend.service.InsumosDePrueba.pieza;
import static com.weclover.backend.service.InsumosDePrueba.pin;
import static com.weclover.backend.service.InsumosDePrueba.productoConDisenoCargado;
import static com.weclover.backend.service.InsumosDePrueba.punosYCintura;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.weclover.backend.dto.producto.AsignacionInsumoPiezaItemRequest;
import com.weclover.backend.dto.producto.AsignacionesInsumoPiezaRequest;
import com.weclover.backend.dto.producto.AsignacionesInsumoResponse;
import com.weclover.backend.dto.producto.GrupoAsignacionInsumoResponse;
import com.weclover.backend.dto.producto.PinInsumoResponse;
import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.PatronCortePosicionPieza;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoInsumoSecundario;
import com.weclover.backend.exception.ForbiddenException;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.repository.ProductoRepository;

/** Lectura y guardado de las asignaciones pin → fila de insumo de un producto. */
@ExtendWith(MockitoExtension.class)
class ProductoInsumoPiezaServiceTest {

    private static final Long ID_ACTOR = 7L;
    private static final Long ID_PRODUCTO = 1L;

    private static final PaletaColores ROJO_FRIZA = color(1L, "Rojo", FRIZA);
    private static final PaletaColores ROJO_JERSEY = color(2L, "Rojo", JERSEY);
    private static final PaletaColores ROJO_RIBB = color(3L, "Rojo", RIBB);
    private static final PaletaColores BLANCO_RIBB = color(4L, "Blanco", RIBB);

    @Mock private ProductoRepository productoRepository;
    @Mock private AutorizacionService autorizacionService;

    @InjectMocks
    private ProductoInsumoPiezaService service;

    private Producto buzo;
    private ProductoInsumoSecundario filaCapucha;
    private ProductoInsumoSecundario punosRojos;
    private ProductoInsumoSecundario punosBlancos;
    private PatronCortePosicionPieza pinEspalda;
    private PatronCortePosicionPieza pinCapucha;
    private PatronCortePosicionPieza pinPuno1;
    private PatronCortePosicionPieza pinPuno2;
    private PatronCortePosicionPieza pinCintura;

    @BeforeEach
    void setUp() {
        buzo = productoConDisenoCargado(ID_PRODUCTO, "Buzo", 10, ROJO_FRIZA, 600);
        pinEspalda = pin(100L, buzo, 0, pieza(10L, "Espalda", null), null);
        pinCapucha = pin(103L, buzo, 0, pieza(12L, "Capucha", TIPO_CAPUCHA), null);
        pinPuno1 = pin(104L, buzo, 0, pieza(13L, "Puño", TIPO_PUNOS_CINTURA), null);
        pinPuno2 = pin(105L, buzo, 0, pieza(13L, "Puño", TIPO_PUNOS_CINTURA), null);
        pinCintura = pin(106L, buzo, 0, pieza(14L, "Cintura", TIPO_PUNOS_CINTURA), null);

        filaCapucha = conId(20L, capucha(ROJO_JERSEY, 70f));
        punosRojos = conId(21L, punosYCintura(ROJO_RIBB, 30f));
        punosBlancos = conId(22L, punosYCintura(BLANCO_RIBB, 30f));
        agregar(buzo, filaCapucha, punosRojos, punosBlancos);

        lenient().when(productoRepository.findById(ID_PRODUCTO)).thenReturn(Optional.of(buzo));
        lenient().when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(productoRepository.saveAndFlush(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ProductoInsumoSecundario conId(Long id, ProductoInsumoSecundario fila) {
        fila.setId(id);
        return fila;
    }

    private static AsignacionInsumoPiezaItemRequest a(PatronCortePosicionPieza pin, ProductoInsumoSecundario fila) {
        return new AsignacionInsumoPiezaItemRequest(pin.getId(), fila.getId());
    }

    private AsignacionesInsumoResponse guardar(AsignacionInsumoPiezaItemRequest... items) {
        return service.guardar(ID_PRODUCTO, new AsignacionesInsumoPiezaRequest(Arrays.asList(items)), ID_ACTOR);
    }

    private static GrupoAsignacionInsumoResponse grupo(AsignacionesInsumoResponse respuesta, String codigo) {
        return respuesta.grupos().stream().filter(g -> g.codigoTipoInsumo().equals(codigo)).findFirst().orElseThrow();
    }

    @Test
    void obtener_sinAsignaciones_informaLosPinesPendientesDelTipoConVariasFilas() {
        AsignacionesInsumoResponse respuesta = service.obtener(ID_PRODUCTO);

        assertThat(respuesta.pinesSinAsignar()).isEqualTo(3);
        GrupoAsignacionInsumoResponse punos = grupo(respuesta, "PUNOS_CINTURA");
        assertThat(punos.requiereAsignacion()).isTrue();
        assertThat(punos.idsProductoInsumo()).containsExactly(21L, 22L);
        assertThat(punos.pinesSinAsignar()).isEqualTo(3);
        assertThat(punos.pines())
            .extracting(PinInsumoResponse::idPosicionPieza, PinInsumoResponse::etiqueta, PinInsumoResponse::idProductoInsumo)
            .containsExactly(
                tuple(104L, "Puño 1", null),
                tuple(105L, "Puño 2", null),
                tuple(106L, "Cintura", null));

        GrupoAsignacionInsumoResponse capuchaGrupo = grupo(respuesta, "CAPUCHA");
        assertThat(capuchaGrupo.requiereAsignacion()).isFalse();
        assertThat(capuchaGrupo.pinesSinAsignar()).isZero();
    }

    @Test
    void guardar_asignaCadaPinASuFila() {
        AsignacionesInsumoResponse respuesta = guardar(a(pinPuno1, punosRojos), a(pinPuno2, punosRojos), a(pinCintura, punosBlancos));

        assertThat(respuesta.pinesSinAsignar()).isZero();
        assertThat(punosRojos.getAsignacionesPieza()).extracting(x -> x.getPosicionPieza().getId()).containsExactly(104L, 105L);
        assertThat(punosBlancos.getAsignacionesPieza()).extracting(x -> x.getPosicionPieza().getId()).containsExactly(106L);
        assertThat(punosRojos.getAsignacionesPieza()).allSatisfy(x -> {
            assertThat(x.getProducto()).isSameAs(buzo);
            assertThat(x.getProductoInsumo()).isSameAs(punosRojos);
        });
        assertThat(grupo(respuesta, "PUNOS_CINTURA").pines())
            .extracting(PinInsumoResponse::idProductoInsumo).containsExactly(21L, 21L, 22L);
    }

    @Test
    void guardar_parcial_seAceptaYAvisaCuantosFaltan() {
        AsignacionesInsumoResponse respuesta = guardar(a(pinPuno1, punosRojos));

        assertThat(respuesta.pinesSinAsignar()).isEqualTo(2);
    }

    @Test
    void guardar_reemplazaLasAsignacionesAnteriores() {
        asignar(punosRojos, pinPuno1);
        asignar(punosRojos, pinCintura);

        guardar(a(pinPuno1, punosBlancos));

        assertThat(punosRojos.getAsignacionesPieza()).isEmpty();
        assertThat(punosBlancos.getAsignacionesPieza()).extracting(x -> x.getPosicionPieza().getId()).containsExactly(104L);
        verify(productoRepository).saveAndFlush(buzo);
    }

    @Test
    void guardar_pinDeOtraMolderia_seRechaza() {
        assertThatThrownBy(() -> guardar(new AsignacionInsumoPiezaItemRequest(999L, 21L)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("El pin 999 no pertenece a la moldería de este producto");
    }

    @Test
    void guardar_filaDeOtroProducto_seRechaza() {
        assertThatThrownBy(() -> guardar(new AsignacionInsumoPiezaItemRequest(104L, 999L)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("El insumo 999 no pertenece a este producto");
    }

    @Test
    void guardar_elMismoPinDosVeces_seRechaza() {
        assertThatThrownBy(() -> guardar(a(pinPuno1, punosRojos), a(pinPuno1, punosBlancos)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("El pin \"Puño 1\" está asignado más de una vez");
    }

    @Test
    void guardar_pinDelCuerpo_seRechaza() {
        assertThatThrownBy(() -> guardar(a(pinEspalda, punosRojos)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("es del cuerpo de la prenda");
    }

    @Test
    void guardar_filaDeOtroTipoQueLaPiezaDelPin_seRechaza() {
        assertThatThrownBy(() -> guardar(a(pinCapucha, punosRojos)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("La pieza \"Capucha\" es de Capucha y no se puede asignar a \"Puños y cintura\"");
        assertThat(punosRojos.getAsignacionesPieza()).isEmpty();
    }

    @Test
    void guardar_sinRolDeCargaDeDiseno_seRechazaSinTocarNada() {
        doThrow(new ForbiddenException("sin permiso"))
            .when(autorizacionService).verificarRolPermitido(eq(ID_ACTOR), anySet());

        assertThatThrownBy(() -> guardar(a(pinPuno1, punosRojos))).isInstanceOf(ForbiddenException.class);
        verify(productoRepository, never()).save(any());
        assertThat(List.of(punosRojos, punosBlancos)).allSatisfy(f -> assertThat(f.getAsignacionesPieza()).isEmpty());
    }
}
