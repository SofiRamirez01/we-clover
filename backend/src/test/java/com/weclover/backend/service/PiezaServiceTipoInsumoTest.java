package com.weclover.backend.service;

import static com.weclover.backend.service.InsumosDePrueba.FRIZA;
import static com.weclover.backend.service.InsumosDePrueba.JERSEY;
import static com.weclover.backend.service.InsumosDePrueba.RIBB;
import static com.weclover.backend.service.InsumosDePrueba.TIPO_CAPUCHA;
import static com.weclover.backend.service.InsumosDePrueba.TIPO_ESTAMPADO;
import static com.weclover.backend.service.InsumosDePrueba.TIPO_PUNOS_CINTURA;
import static com.weclover.backend.service.InsumosDePrueba.agregar;
import static com.weclover.backend.service.InsumosDePrueba.asignar;
import static com.weclover.backend.service.InsumosDePrueba.color;
import static com.weclover.backend.service.InsumosDePrueba.pin;
import static com.weclover.backend.service.InsumosDePrueba.productoConDisenoCargado;
import static com.weclover.backend.service.InsumosDePrueba.punosYCintura;
import static com.weclover.backend.service.InsumosDePrueba.tipoInsumo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.weclover.backend.dto.pieza.PiezaGeometriaCalculoResponse;
import com.weclover.backend.dto.pieza.PiezaResponse;
import com.weclover.backend.dto.pieza.SegmentoDto;
import com.weclover.backend.entity.GrupoTalle;
import com.weclover.backend.entity.PatronCortePosicionPieza;
import com.weclover.backend.entity.Pieza;
import com.weclover.backend.entity.PiezaTalle;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoInsumoSecundario;
import com.weclover.backend.entity.TablaTalle;
import com.weclover.backend.entity.TipoInsumo;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.mapper.PiezaMapper;
import com.weclover.backend.repository.GrupoTalleRepository;
import com.weclover.backend.repository.PiezaRepository;
import com.weclover.backend.repository.PiezaTalleRepository;
import com.weclover.backend.repository.ProductoInsumoPiezaRepository;
import com.weclover.backend.repository.TablaTalleRepository;
import com.weclover.backend.repository.TipoInsumoRepository;

import tools.jackson.databind.ObjectMapper;

/** "Tela de la pieza": el tipo de insumo en el que se corta una Pieza (null = cuerpo). */
@ExtendWith(MockitoExtension.class)
class PiezaServiceTipoInsumoTest {

    private static final Long ID_ACTOR = 14L;
    private static final GrupoTalle GRUPO = GrupoTalle.builder().id(1L).nombre("Campera/Buzo").build();
    private static final TablaTalle TALLE = TablaTalle.builder().id(2L).grupoTalle(GRUPO).talle("2").build();
    private static final TipoInsumo TIPO_INACTIVO = tipoInsumo(9L, "CUELLO", "Cuello", true, JERSEY);

    @Mock private PiezaRepository piezaRepository;
    @Mock private PiezaTalleRepository piezaTalleRepository;
    @Mock private GrupoTalleRepository grupoTalleRepository;
    @Mock private TablaTalleRepository tablaTalleRepository;
    @Mock private TipoInsumoRepository tipoInsumoRepository;
    @Mock private ProductoInsumoPiezaRepository productoInsumoPiezaRepository;
    @Mock private PiezaMapper piezaMapper;
    @Mock private PiezaGeometriaClient piezaGeometriaClient;
    @Mock private AutorizacionService autorizacionService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private PiezaService service;

    @BeforeEach
    void setUp() {
        TIPO_INACTIVO.setActivo(false);
        lenient().when(grupoTalleRepository.findById(1L)).thenReturn(Optional.of(GRUPO));
        lenient().when(tablaTalleRepository.findById(2L)).thenReturn(Optional.of(TALLE));
        for (TipoInsumo tipo : List.of(TIPO_CAPUCHA, TIPO_PUNOS_CINTURA, TIPO_ESTAMPADO, TIPO_INACTIVO)) {
            lenient().when(tipoInsumoRepository.findById(tipo.getId())).thenReturn(Optional.of(tipo));
        }
        lenient().when(piezaGeometriaClient.calcularBase(any(), anyBoolean()))
            .thenReturn(new PiezaGeometriaCalculoResponse(List.of(), 100, 10, 10, 40));
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        lenient().when(piezaRepository.save(any(Pieza.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(piezaTalleRepository.save(any(PiezaTalle.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(piezaMapper.toResponse(any(Pieza.class))).thenAnswer(inv -> {
            Pieza pieza = inv.getArgument(0);
            TipoInsumo tipo = pieza.getTipoInsumo();
            return new PiezaResponse(pieza.getId(), pieza.getNombre(), 1L, "Campera/Buzo", 2L, "2", false, 0, 0, true,
                tipo != null ? tipo.getId() : null, tipo != null ? tipo.getNombre() : null);
        });
    }

    private PiezaResponse crear(Long idTipoInsumo) {
        return service.crear("Capucha", 1L, 2L, List.<SegmentoDto>of(), false, idTipoInsumo, ID_ACTOR);
    }

    private Pieza piezaGuardada(TipoInsumo tipo) {
        Pieza pieza = Pieza.builder().id(13L).nombre("Puño").grupoTalle(GRUPO).talleBase(TALLE).tipoInsumo(tipo).build();
        lenient().when(piezaRepository.findById(13L)).thenReturn(Optional.of(pieza));
        return pieza;
    }

    private PiezaResponse actualizar(Long idTipoInsumo) {
        return service.actualizar(13L, "Puño", 1L, 2L, List.<SegmentoDto>of(), false, idTipoInsumo, ID_ACTOR);
    }

    @Test
    void crearSinTipo_esUnaPiezaDelCuerpo() {
        PiezaResponse respuesta = crear(null);

        ArgumentCaptor<Pieza> captor = ArgumentCaptor.forClass(Pieza.class);
        verify(piezaRepository).save(captor.capture());
        assertThat(captor.getValue().getTipoInsumo()).isNull();
        assertThat(respuesta.idTipoInsumo()).isNull();
    }

    @Test
    void crearConUnTipoQueConsumeMaterial_loGuarda() {
        PiezaResponse respuesta = crear(TIPO_CAPUCHA.getId());

        ArgumentCaptor<Pieza> captor = ArgumentCaptor.forClass(Pieza.class);
        verify(piezaRepository).save(captor.capture());
        assertThat(captor.getValue().getTipoInsumo()).isSameAs(TIPO_CAPUCHA);
        assertThat(respuesta.nombreTipoInsumo()).isEqualTo("Capucha");
    }

    @Test
    void unTipoSinMaterial_noSePuedeElegirComoTelaDeLaPieza() {
        assertThatThrownBy(() -> crear(TIPO_ESTAMPADO.getId()))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("\"Estampado\" no es una tela: no se puede elegir como tela de una pieza");
        verify(piezaRepository, never()).save(any());
    }

    @Test
    void unTipoInactivo_noSePuedeElegir_peroSeConservaSiLaPiezaYaLoTenia() {
        assertThatThrownBy(() -> crear(TIPO_INACTIVO.getId()))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("El tipo de insumo \"Cuello\" ya no está disponible");

        Pieza pieza = piezaGuardada(TIPO_INACTIVO);
        actualizar(TIPO_INACTIVO.getId());
        assertThat(pieza.getTipoInsumo()).isSameAs(TIPO_INACTIVO);
    }

    @Test
    void cambiarLaTelaDeLaPieza_descartaLasAsignacionesDeSusPines() {
        Pieza pieza = piezaGuardada(TIPO_PUNOS_CINTURA);
        Producto buzo = productoConDisenoCargado(1L, "Buzo", 10, color(1L, "Rojo", FRIZA), 600);
        PatronCortePosicionPieza pinPuno = pin(104L, buzo, 0, pieza, null);
        ProductoInsumoSecundario rojos = punosYCintura(color(3L, "Rojo", RIBB), 30f);
        ProductoInsumoSecundario blancos = punosYCintura(color(4L, "Blanco", RIBB), 30f);
        agregar(buzo, rojos, blancos);
        asignar(rojos, pinPuno);
        lenient().when(productoInsumoPiezaRepository.findByPosicionPieza_Pieza_Id(13L))
            .thenReturn(List.copyOf(rojos.getAsignacionesPieza()));

        actualizar(null);

        assertThat(pieza.getTipoInsumo()).isNull();
        assertThat(rojos.getAsignacionesPieza()).isEmpty();
    }

    @Test
    void guardarSinCambiarLaTela_noTocaLasAsignaciones() {
        piezaGuardada(TIPO_PUNOS_CINTURA);

        actualizar(TIPO_PUNOS_CINTURA.getId());

        verify(productoInsumoPiezaRepository, never()).findByPosicionPieza_Pieza_Id(any());
    }
}
