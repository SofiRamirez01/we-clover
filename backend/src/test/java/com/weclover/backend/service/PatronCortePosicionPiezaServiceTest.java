package com.weclover.backend.service;

import static com.weclover.backend.service.InsumosDePrueba.FRIZA;
import static com.weclover.backend.service.InsumosDePrueba.RIBB;
import static com.weclover.backend.service.InsumosDePrueba.TIPO_CAPUCHA;
import static com.weclover.backend.service.InsumosDePrueba.TIPO_PUNOS_CINTURA;
import static com.weclover.backend.service.InsumosDePrueba.agregar;
import static com.weclover.backend.service.InsumosDePrueba.asignar;
import static com.weclover.backend.service.InsumosDePrueba.color;
import static com.weclover.backend.service.InsumosDePrueba.pin;
import static com.weclover.backend.service.InsumosDePrueba.productoConDisenoCargado;
import static com.weclover.backend.service.InsumosDePrueba.punosYCintura;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.weclover.backend.dto.patroncorte.PatronCortePosicionPiezaRequest;
import com.weclover.backend.dto.patroncorte.PatronCortePosicionPiezaResponse;
import com.weclover.backend.dto.patroncorte.PinEliminadoResponse;
import com.weclover.backend.entity.GrupoTalle;
import com.weclover.backend.entity.PatronCorte;
import com.weclover.backend.entity.PatronCorteColor;
import com.weclover.backend.entity.PatronCortePosicionPieza;
import com.weclover.backend.entity.Pieza;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoInsumoPieza;
import com.weclover.backend.entity.ProductoInsumoSecundario;
import com.weclover.backend.entity.TipoInsumo;
import com.weclover.backend.entity.TipoPrenda;
import com.weclover.backend.repository.PatronCortePosicionPiezaRepository;
import com.weclover.backend.repository.PatronCorteRepository;
import com.weclover.backend.repository.PiezaRepository;
import com.weclover.backend.repository.ProductoInsumoPiezaRepository;

/** Al borrar un pin o pasarlo a una pieza de otro tipo de insumo, se descartan las asignaciones
 *  de las fichas que lo usaban y se informa cuántas fueron. */
@ExtendWith(MockitoExtension.class)
class PatronCortePosicionPiezaServiceTest {

    private static final Long ID_ACTOR = 7L;
    private static final GrupoTalle GRUPO = GrupoTalle.builder().id(1L).nombre("Campera/Buzo").build();

    @Mock private PatronCorteRepository patronCorteRepository;
    @Mock private PatronCortePosicionPiezaRepository patronCortePosicionPiezaRepository;
    @Mock private PiezaRepository piezaRepository;
    @Mock private ProductoInsumoPiezaRepository productoInsumoPiezaRepository;
    @Mock private PiezaService piezaService;
    @Mock private AutorizacionService autorizacionService;

    @InjectMocks
    private PatronCortePosicionPiezaService service;

    private Producto buzo;
    private PatronCorte molderia;
    private PatronCorteColor posicionColor;
    private PatronCortePosicionPieza pinPuno;
    private ProductoInsumoSecundario punosRojos;
    private ProductoInsumoSecundario punosBlancos;

    @BeforeEach
    void setUp() {
        buzo = productoConDisenoCargado(1L, "Buzo", 10, color(1L, "Rojo", FRIZA), 600);
        molderia = buzo.getPatronCorte();
        molderia.setTiposPrenda(new ArrayList<>(List.of(TipoPrenda.builder().id(1L).nombre("Buzo").grupoTalle(GRUPO).build())));
        posicionColor = molderia.getColores().get(0);
        pinPuno = pin(104L, buzo, 0, pieza(13L, "Puño", TIPO_PUNOS_CINTURA), null);

        punosRojos = punosYCintura(color(3L, "Rojo", RIBB), 30f);
        punosBlancos = punosYCintura(color(4L, "Blanco", RIBB), 30f);
        agregar(buzo, punosRojos, punosBlancos);

        lenient().when(patronCorteRepository.findById(molderia.getId())).thenReturn(Optional.of(molderia));
        lenient().when(patronCortePosicionPiezaRepository.save(any(PatronCortePosicionPieza.class)))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    private Pieza pieza(Long id, String nombre, TipoInsumo tipoInsumo) {
        Pieza pieza = Pieza.builder().id(id).nombre(nombre).grupoTalle(GRUPO).tipoInsumo(tipoInsumo).build();
        lenient().when(piezaRepository.findById(id)).thenReturn(Optional.of(pieza));
        return pieza;
    }

    /** Deja al pin asignado a esa fila, como lo devolvería el repositorio. */
    private void asignado(ProductoInsumoSecundario fila) {
        asignar(fila, pinPuno);
        List<ProductoInsumoPieza> asignaciones = List.copyOf(fila.getAsignacionesPieza());
        lenient().when(productoInsumoPiezaRepository.findByPosicionPieza_Id(pinPuno.getId())).thenReturn(asignaciones);
    }

    private PatronCortePosicionPiezaResponse cambiarPieza(Pieza nueva) {
        return service.actualizar(molderia.getId(), posicionColor.getId(), pinPuno.getId(),
            new PatronCortePosicionPiezaRequest(nueva.getId(), 0.5, 0.5, null), ID_ACTOR);
    }

    @Test
    void eliminarPinAsignado_descartaLaAsignacionAntesDeBorrarloEInformaCuantas() {
        asignado(punosRojos);

        PinEliminadoResponse respuesta = service.eliminar(molderia.getId(), posicionColor.getId(), pinPuno.getId(), ID_ACTOR);

        assertThat(respuesta.asignacionesDescartadas()).isEqualTo(1);
        assertThat(punosRojos.getAsignacionesPieza()).isEmpty();
        assertThat(posicionColor.getPosicionesPieza()).doesNotContain(pinPuno);
        verify(productoInsumoPiezaRepository).flush();
    }

    @Test
    void eliminarPinSinAsignaciones_informaCero() {
        PinEliminadoResponse respuesta = service.eliminar(molderia.getId(), posicionColor.getId(), pinPuno.getId(), ID_ACTOR);

        assertThat(respuesta.asignacionesDescartadas()).isZero();
        assertThat(posicionColor.getPosicionesPieza()).isEmpty();
        verify(productoInsumoPiezaRepository, never()).flush();
    }

    @Test
    void cambiarAUnaPiezaDeOtroTipoDeInsumo_descartaLaAsignacion() {
        asignado(punosBlancos);

        PatronCortePosicionPiezaResponse respuesta = cambiarPieza(pieza(12L, "Capucha", TIPO_CAPUCHA));

        assertThat(respuesta.asignacionesDescartadas()).isEqualTo(1);
        assertThat(punosBlancos.getAsignacionesPieza()).isEmpty();
        assertThat(pinPuno.getPieza().getNombre()).isEqualTo("Capucha");
    }

    @Test
    void cambiarAUnaPiezaDelCuerpo_descartaLaAsignacion() {
        asignado(punosRojos);

        PatronCortePosicionPiezaResponse respuesta = cambiarPieza(pieza(10L, "Espalda", null));

        assertThat(respuesta.asignacionesDescartadas()).isEqualTo(1);
        assertThat(punosRojos.getAsignacionesPieza()).isEmpty();
    }

    @Test
    void cambiarAOtraPiezaDelMismoTipoDeInsumo_conservaLaAsignacion() {
        asignado(punosRojos);

        PatronCortePosicionPiezaResponse respuesta = cambiarPieza(pieza(14L, "Cintura", TIPO_PUNOS_CINTURA));

        assertThat(respuesta.asignacionesDescartadas()).isZero();
        assertThat(punosRojos.getAsignacionesPieza()).hasSize(1);
        assertThat(pinPuno.getPieza().getNombre()).isEqualTo("Cintura");
    }
}
