package com.weclover.backend.service;

import static com.weclover.backend.service.InsumosDePrueba.CIERRE;
import static com.weclover.backend.service.InsumosDePrueba.FRIZA;
import static com.weclover.backend.service.InsumosDePrueba.JERSEY;
import static com.weclover.backend.service.InsumosDePrueba.RIBB;
import static com.weclover.backend.service.InsumosDePrueba.TIPO_CAPUCHA;
import static com.weclover.backend.service.InsumosDePrueba.TIPO_CIERRE;
import static com.weclover.backend.service.InsumosDePrueba.TIPO_ESTAMPADO;
import static com.weclover.backend.service.InsumosDePrueba.TIPO_PUNOS_CINTURA;
import static com.weclover.backend.service.InsumosDePrueba.agregar;
import static com.weclover.backend.service.InsumosDePrueba.asignar;
import static com.weclover.backend.service.InsumosDePrueba.capucha;
import static com.weclover.backend.service.InsumosDePrueba.color;
import static com.weclover.backend.service.InsumosDePrueba.libre;
import static com.weclover.backend.service.InsumosDePrueba.pieza;
import static com.weclover.backend.service.InsumosDePrueba.pin;
import static com.weclover.backend.service.InsumosDePrueba.productoConDisenoCargado;
import static com.weclover.backend.service.InsumosDePrueba.punosYCintura;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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

import com.weclover.backend.dto.producto.ActualizarPatronCorteRequest;
import com.weclover.backend.dto.producto.ProductoColorItemRequest;
import com.weclover.backend.dto.producto.ProductoColoresRequest;
import com.weclover.backend.dto.producto.ProductoInsumoSecundarioItemRequest;
import com.weclover.backend.dto.producto.ProductoInsumosSecundariosRequest;
import com.weclover.backend.dto.producto.ProductoResponse;
import com.weclover.backend.entity.EtapaProduccion;
import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.PatronCorte;
import com.weclover.backend.entity.PatronCortePosicionPieza;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoInsumoSecundario;
import com.weclover.backend.entity.TipoInsumo;
import com.weclover.backend.entity.TipoTela;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.mapper.ProductoMapper;
import com.weclover.backend.repository.PaletaColoresRepository;
import com.weclover.backend.repository.PatronCorteRepository;
import com.weclover.backend.repository.ProductoRepository;
import com.weclover.backend.repository.TipoInsumoRepository;
import com.weclover.backend.repository.TipoTelaRepository;

/**
 * Insumos secundarios identificados por tipo (TipoInsumo) y no por el texto de la descripción:
 * guardado desde la ficha y sugerencia automática al marcar los colores.
 */
@ExtendWith(MockitoExtension.class)
class ProductoServiceInsumosTest {

    private static final Long ID_ACTOR = 7L;
    private static final Long ID_PRODUCTO = 10L;

    private static final PaletaColores ROJO_FRIZA = color(1L, "Rojo", FRIZA);
    private static final PaletaColores ROJO_JERSEY = color(2L, "Rojo", JERSEY);
    private static final PaletaColores ROJO_RIBB = color(3L, "Rojo", RIBB);
    private static final PaletaColores BLANCO_RIBB = color(4L, "Blanco", RIBB);
    private static final PaletaColores ROJO_CIERRE = color(5L, "Rojo", CIERRE);

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
        List<TipoInsumo> tipos = List.of(TIPO_CAPUCHA, TIPO_PUNOS_CINTURA, TIPO_CIERRE, TIPO_ESTAMPADO);
        lenient().when(tipoInsumoRepository.findAll()).thenReturn(tipos);
        for (TipoInsumo tipo : tipos) {
            lenient().when(tipoInsumoRepository.findByCodigo(tipo.getCodigo())).thenReturn(Optional.of(tipo));
        }
        for (TipoTela tela : List.of(FRIZA, JERSEY, RIBB, CIERRE)) {
            lenient().when(tipoTelaRepository.findById(tela.getId())).thenReturn(Optional.of(tela));
            lenient().when(tipoTelaRepository.findByCodigo(tela.getCodigo())).thenReturn(Optional.of(tela));
        }
        for (PaletaColores color : List.of(ROJO_FRIZA, ROJO_JERSEY, ROJO_RIBB, BLANCO_RIBB, ROJO_CIERRE)) {
            lenient().when(paletaColoresRepository.findById(color.getId())).thenReturn(Optional.of(color));
            lenient().when(paletaColoresRepository.findByNombreIgnoreCaseAndTipoTela(color.getNombre(), color.getTipoTela()))
                .thenReturn(Optional.of(color));
        }
        lenient().when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(productoRepository.saveAndFlush(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(productoMapper.toResponse(any(Producto.class))).thenReturn(new ProductoResponse(ID_PRODUCTO, null, null,
            null, List.of(), null, null, null, null, 10, 0f, 0f, null, null, null, null, null, List.of(), List.of()));
    }

    private Producto productoGuardado(String tipoPrenda) {
        Producto producto = productoConDisenoCargado(ID_PRODUCTO, tipoPrenda, 10, ROJO_FRIZA, 600);
        producto.setPedido(Pedido.builder().id(1L).build());
        lenient().when(productoRepository.findById(ID_PRODUCTO)).thenReturn(Optional.of(producto));
        return producto;
    }

    private static ProductoInsumoSecundarioItemRequest item(TipoInsumo tipo, String descripcion, PaletaColores color, Float cantidad) {
        return new ProductoInsumoSecundarioItemRequest(
            null,
            tipo != null ? tipo.getId() : null,
            descripcion,
            color != null ? color.getTipoTela().getId() : null,
            color != null ? color.getId() : null,
            cantidad, null);
    }

    private void guardar(ProductoInsumoSecundarioItemRequest... items) {
        service.actualizarInsumosSecundarios(ID_PRODUCTO, new ProductoInsumosSecundariosRequest(List.of(items)), ID_ACTOR);
    }

    private static ProductoInsumoSecundario unicaFila(Producto producto) {
        assertThat(producto.getInsumosSecundarios()).hasSize(1);
        return producto.getInsumosSecundarios().get(0);
    }

    // ---------- Guardado desde la ficha ----------

    @Test
    void filaConTipo_guardaElTipoYCompletaLaDescripcionConSuNombre() {
        Producto buzo = productoGuardado("Buzo");

        guardar(item(TIPO_CAPUCHA, null, ROJO_JERSEY, 70f));

        ProductoInsumoSecundario fila = unicaFila(buzo);
        assertThat(fila.getTipoInsumo()).isSameAs(TIPO_CAPUCHA);
        assertThat(fila.getDescripcion()).isEqualTo("Capucha");
        assertThat(fila.getTipoTela()).isSameAs(JERSEY);
        assertThat(fila.getColor()).isSameAs(ROJO_JERSEY);
        assertThat(fila.getCantidad()).isEqualTo(70f);
    }

    @Test
    void filaSinTipoPeroConElNombreExactoDeUno_quedaAsociadaAEseTipo() {
        Producto buzo = productoGuardado("Buzo");

        guardar(item(null, " puños y Cintura ", ROJO_RIBB, 60f));

        assertThat(unicaFila(buzo).getTipoInsumo()).isSameAs(TIPO_PUNOS_CINTURA);
    }

    @Test
    void filaSinTipoConOtroTexto_quedaComoInsumoLibre() {
        Producto buzo = productoGuardado("Buzo");

        guardar(item(null, "Bolsillo", ROJO_FRIZA, 20f));

        ProductoInsumoSecundario fila = unicaFila(buzo);
        assertThat(fila.getTipoInsumo()).isNull();
        assertThat(fila.getDescripcion()).isEqualTo("Bolsillo");
    }

    @Test
    void dosFilasDelMismoTipo_seGuardanLasDos() {
        Producto buzo = productoGuardado("Buzo");

        guardar(item(TIPO_PUNOS_CINTURA, null, ROJO_RIBB, 30f), item(TIPO_PUNOS_CINTURA, null, BLANCO_RIBB, 30f));

        assertThat(buzo.getInsumosSecundarios())
            .extracting(ProductoInsumoSecundario::getTipoInsumo, ProductoInsumoSecundario::getColor)
            .containsExactly(
                tuple(TIPO_PUNOS_CINTURA, ROJO_RIBB),
                tuple(TIPO_PUNOS_CINTURA, BLANCO_RIBB));
    }

    @Test
    void estampado_seGuardaSinMaterial_yHaceAplicarLaEtapa() {
        Producto remera = productoGuardado("Remera");

        guardar(item(TIPO_ESTAMPADO, null, null, null));

        ProductoInsumoSecundario fila = unicaFila(remera);
        assertThat(fila.getTipoInsumo()).isSameAs(TIPO_ESTAMPADO);
        assertThat(fila.getDescripcion()).isEqualTo("Estampado");
        assertThat(fila.getTipoTela()).isNull();
        assertThat(fila.getColor()).isNull();
        assertThat(fila.getCantidad()).isNull();
        assertThat(EtapaProduccionAplicabilidad.etapasAplicables(remera)).contains(EtapaProduccion.ESTAMPADO);
        verify(productoEtapaProduccionService).sincronizarEtapas(remera);
    }

    @Test
    void estampadoConTelaColorOCantidad_seRechaza() {
        productoGuardado("Remera");

        assertThatThrownBy(() -> guardar(item(TIPO_ESTAMPADO, null, ROJO_JERSEY, 10f)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("\"Estampado\" no lleva tela, color ni cantidad");
        assertThatThrownBy(() -> guardar(item(TIPO_ESTAMPADO, null, null, 10f)))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void filaConMaterialIncompleta_seRechaza() {
        productoGuardado("Buzo");

        assertThatThrownBy(() -> guardar(item(TIPO_CAPUCHA, null, null, 70f)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("Debe indicar el tipo de tela de \"Capucha\"");
        assertThatThrownBy(() -> guardar(item(TIPO_CAPUCHA, null, ROJO_JERSEY, null)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("La cantidad de \"Capucha\" debe ser mayor a cero");
        assertThatThrownBy(() -> guardar(item(TIPO_CAPUCHA, null, ROJO_JERSEY, 0f)))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void insumoLibreSinDescripcion_seRechaza() {
        productoGuardado("Buzo");

        assertThatThrownBy(() -> guardar(item(null, "  ", ROJO_FRIZA, 20f)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("Debe indicar para qué es este insumo");
    }

    // ---------- Guardado por id y asignaciones de pines ----------

    private static ProductoInsumoSecundarioItemRequest itemExistente(ProductoInsumoSecundario fila, TipoInsumo tipo, PaletaColores color, Float cantidad) {
        return new ProductoInsumoSecundarioItemRequest(fila.getId(), tipo != null ? tipo.getId() : null, null,
            color != null ? color.getTipoTela().getId() : null, color != null ? color.getId() : null, cantidad, null);
    }

    /** Buzo con capucha y dos filas de puños (ids 20, 21, 22) y un pin de puño asignado a cada una. */
    private Producto buzoConPunosAsignados() {
        Producto buzo = productoGuardado("Buzo");
        ProductoInsumoSecundario filaCapucha = capucha(ROJO_JERSEY, 70f);
        ProductoInsumoSecundario punosRojos = punosYCintura(ROJO_RIBB, 30f);
        ProductoInsumoSecundario punosBlancos = punosYCintura(BLANCO_RIBB, 30f);
        filaCapucha.setId(20L);
        punosRojos.setId(21L);
        punosBlancos.setId(22L);
        agregar(buzo, filaCapucha, punosRojos, punosBlancos);
        PatronCortePosicionPieza pinPuno1 = pin(104L, buzo, 0, pieza(13L, "Puño", TIPO_PUNOS_CINTURA), null);
        PatronCortePosicionPieza pinPuno2 = pin(105L, buzo, 0, pieza(13L, "Puño", TIPO_PUNOS_CINTURA), null);
        asignar(punosRojos, pinPuno1);
        asignar(punosBlancos, pinPuno2);
        return buzo;
    }

    @Test
    void filaConId_seActualizaEnSuLugarYConservaSusPinesAsignados() {
        Producto buzo = buzoConPunosAsignados();
        ProductoInsumoSecundario filaCapucha = buzo.getInsumosSecundarios().get(0);
        ProductoInsumoSecundario punosRojos = buzo.getInsumosSecundarios().get(1);
        ProductoInsumoSecundario punosBlancos = buzo.getInsumosSecundarios().get(2);

        guardar(
            itemExistente(filaCapucha, TIPO_CAPUCHA, ROJO_JERSEY, 65f),
            itemExistente(punosRojos, TIPO_PUNOS_CINTURA, ROJO_RIBB, 40f),
            itemExistente(punosBlancos, TIPO_PUNOS_CINTURA, BLANCO_RIBB, 20f));

        assertThat(buzo.getInsumosSecundarios()).hasSize(3);
        assertThat(buzo.getInsumosSecundarios().get(1)).isSameAs(punosRojos);
        assertThat(filaCapucha.getCantidad()).isEqualTo(65f);
        assertThat(punosRojos.getCantidad()).isEqualTo(40f);
        assertThat(punosRojos.getAsignacionesPieza()).hasSize(1);
        assertThat(punosBlancos.getAsignacionesPieza()).hasSize(1);
        assertThat(AsignacionInsumoPieza.pinesSinAsignar(buzo)).isEmpty();
    }

    @Test
    void filaQueNoVieneEnLaLista_seBorra_yLasDemasConservanSusPines() {
        Producto buzo = buzoConPunosAsignados();
        ProductoInsumoSecundario filaCapucha = buzo.getInsumosSecundarios().get(0);
        ProductoInsumoSecundario punosRojos = buzo.getInsumosSecundarios().get(1);

        guardar(
            itemExistente(filaCapucha, TIPO_CAPUCHA, ROJO_JERSEY, 70f),
            itemExistente(punosRojos, TIPO_PUNOS_CINTURA, ROJO_RIBB, 60f));

        assertThat(buzo.getInsumosSecundarios()).hasSize(2);
        assertThat(buzo.getInsumosSecundarios().get(0)).isSameAs(filaCapucha);
        assertThat(buzo.getInsumosSecundarios().get(1)).isSameAs(punosRojos);
        assertThat(punosRojos.getAsignacionesPieza()).hasSize(1);
        // Queda una sola fila de puños: vale para todos sus pines, no hay nada pendiente.
        assertThat(AsignacionInsumoPieza.pinesSinAsignar(buzo)).isEmpty();
    }

    @Test
    void filasNuevasJuntoAExistentes_seAgreganSinTocarLasOtras() {
        Producto buzo = buzoConPunosAsignados();
        ProductoInsumoSecundario filaCapucha = buzo.getInsumosSecundarios().get(0);
        ProductoInsumoSecundario punosRojos = buzo.getInsumosSecundarios().get(1);
        ProductoInsumoSecundario punosBlancos = buzo.getInsumosSecundarios().get(2);

        guardar(
            itemExistente(filaCapucha, TIPO_CAPUCHA, ROJO_JERSEY, 70f),
            itemExistente(punosRojos, TIPO_PUNOS_CINTURA, ROJO_RIBB, 30f),
            itemExistente(punosBlancos, TIPO_PUNOS_CINTURA, BLANCO_RIBB, 30f),
            item(null, "Bolsillo", ROJO_FRIZA, 20f),
            item(null, "Vivo", ROJO_FRIZA, 5f));

        assertThat(buzo.getInsumosSecundarios()).extracting(ProductoInsumoSecundario::getDescripcion)
            .containsExactly("Capucha", "Puños y cintura", "Puños y cintura", "Bolsillo", "Vivo");
        assertThat(punosRojos.getAsignacionesPieza()).hasSize(1);
    }

    @Test
    void filaQueCambiaDeTipoDeInsumo_pierdeSusPinesAsignados() {
        Producto buzo = buzoConPunosAsignados();
        ProductoInsumoSecundario filaCapucha = buzo.getInsumosSecundarios().get(0);
        ProductoInsumoSecundario punosRojos = buzo.getInsumosSecundarios().get(1);
        ProductoInsumoSecundario punosBlancos = buzo.getInsumosSecundarios().get(2);

        guardar(
            itemExistente(filaCapucha, TIPO_CAPUCHA, ROJO_JERSEY, 70f),
            itemExistente(punosRojos, TIPO_PUNOS_CINTURA, ROJO_RIBB, 30f),
            itemExistente(punosBlancos, TIPO_CAPUCHA, ROJO_JERSEY, 30f));

        assertThat(punosBlancos.getTipoInsumo()).isSameAs(TIPO_CAPUCHA);
        assertThat(punosBlancos.getAsignacionesPieza()).isEmpty();
        assertThat(punosRojos.getAsignacionesPieza()).hasSize(1);
    }

    @Test
    void idQueNoEsDelProducto_oRepetido_seRechaza() {
        Producto buzo = buzoConPunosAsignados();
        ProductoInsumoSecundario filaCapucha = buzo.getInsumosSecundarios().get(0);

        assertThatThrownBy(() -> guardar(new ProductoInsumoSecundarioItemRequest(
                999L, TIPO_CAPUCHA.getId(), null, JERSEY.getId(), ROJO_JERSEY.getId(), 70f, null)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("El insumo 999 no pertenece a este producto");
        assertThatThrownBy(() -> guardar(
                itemExistente(filaCapucha, TIPO_CAPUCHA, ROJO_JERSEY, 70f),
                itemExistente(filaCapucha, TIPO_CAPUCHA, ROJO_JERSEY, 70f)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("El insumo 20 está duplicado en la solicitud");
    }

    @Test
    void cambiarLaMolderia_descartaColoresYAsignacionesDePines() {
        Producto buzo = buzoConPunosAsignados();
        PatronCorte otra = PatronCorte.builder().id(101L).nombre("Bicolor")
            .tiposPrenda(List.of(buzo.getTipoPrenda())).colores(new ArrayList<>()).build();
        lenient().when(patronCorteRepository.findById(101L)).thenReturn(Optional.of(otra));

        service.actualizarPatronCorte(ID_PRODUCTO, new ActualizarPatronCorteRequest(101L), ID_ACTOR);

        assertThat(buzo.getPatronCorte()).isSameAs(otra);
        assertThat(buzo.getColores()).isEmpty();
        assertThat(buzo.getInsumosSecundarios()).hasSize(3)
            .allSatisfy(fila -> assertThat(fila.getAsignacionesPieza()).isEmpty());
    }

    @Test
    void reasignarLaMismaMolderia_noDescartaNada() {
        Producto buzo = buzoConPunosAsignados();
        buzo.getPatronCorte().setTiposPrenda(List.of(buzo.getTipoPrenda()));
        lenient().when(patronCorteRepository.findById(100L)).thenReturn(Optional.of(buzo.getPatronCorte()));

        service.actualizarPatronCorte(ID_PRODUCTO, new ActualizarPatronCorteRequest(100L), ID_ACTOR);

        assertThat(buzo.getColores()).hasSize(1);
        assertThat(buzo.getInsumosSecundarios().get(1).getAsignacionesPieza()).hasSize(1);
    }

    // ---------- Reparto de gramos e indicador de edición manual ----------

    private static ProductoInsumoSecundarioItemRequest itemNuevo(TipoInsumo tipo, PaletaColores color, float cantidad, boolean editadaAMano) {
        return new ProductoInsumoSecundarioItemRequest(null, tipo.getId(), null,
            color.getTipoTela().getId(), color.getId(), cantidad, editadaAMano);
    }

    private static List<Float> gramos(Producto producto) {
        return producto.getInsumosSecundarios().stream().map(ProductoInsumoSecundario::getCantidad).toList();
    }

    @Test
    void filasSugeridasDelMismoTipo_seRepartenElSugeridoDeLaTela() {
        Producto buzo = productoGuardado("Buzo");

        guardar(itemNuevo(TIPO_PUNOS_CINTURA, ROJO_RIBB, 60f, false), itemNuevo(TIPO_PUNOS_CINTURA, BLANCO_RIBB, 60f, false));
        assertThat(gramos(buzo)).containsExactly(30f, 30f);

        buzo = productoGuardado("Buzo");
        guardar(itemNuevo(TIPO_PUNOS_CINTURA, ROJO_RIBB, 60f, false), itemNuevo(TIPO_PUNOS_CINTURA, BLANCO_RIBB, 60f, false),
            itemNuevo(TIPO_PUNOS_CINTURA, ROJO_RIBB, 60f, false));
        assertThat(gramos(buzo)).containsExactly(20f, 20f, 20f);
        assertThat(buzo.getInsumosSecundarios()).allSatisfy(fila -> assertThat(fila.getCantidadEditadaManualmente()).isFalse());
    }

    @Test
    void repartoNoExacto_sumaElSugeridoConElRestoAdelante() {
        Producto buzo = productoGuardado("Buzo");

        guardar(itemNuevo(TIPO_CAPUCHA, ROJO_JERSEY, 70f, false), itemNuevo(TIPO_CAPUCHA, ROJO_JERSEY, 70f, false),
            itemNuevo(TIPO_CAPUCHA, ROJO_JERSEY, 70f, false));

        assertThat(gramos(buzo)).containsExactly(24f, 23f, 23f);
    }

    @Test
    void alQuitarUnaFila_lasQueQuedanSeRedistribuyen() {
        Producto buzo = productoGuardado("Buzo");
        guardar(itemNuevo(TIPO_PUNOS_CINTURA, ROJO_RIBB, 60f, false), itemNuevo(TIPO_PUNOS_CINTURA, BLANCO_RIBB, 60f, false));
        ProductoInsumoSecundario rojos = buzo.getInsumosSecundarios().get(0);
        rojos.setId(21L);
        buzo.getInsumosSecundarios().get(1).setId(22L);

        guardar(new ProductoInsumoSecundarioItemRequest(21L, TIPO_PUNOS_CINTURA.getId(), null, RIBB.getId(), ROJO_RIBB.getId(), 30f, false));

        assertThat(gramos(buzo)).containsExactly(60f);
    }

    @Test
    void unaFilaEditadaAMano_congelaTodoElGrupo() {
        Producto buzo = productoGuardado("Buzo");

        guardar(itemNuevo(TIPO_PUNOS_CINTURA, ROJO_RIBB, 50f, true), itemNuevo(TIPO_PUNOS_CINTURA, BLANCO_RIBB, 60f, false));

        assertThat(gramos(buzo)).containsExactly(50f, 60f);
        assertThat(buzo.getInsumosSecundarios()).extracting(ProductoInsumoSecundario::getCantidadEditadaManualmente)
            .containsExactly(true, false);
    }

    @Test
    void elCongelamientoEsPorTipo_noAfectaAOtroTipoDelProducto() {
        Producto buzo = productoGuardado("Buzo");

        guardar(itemNuevo(TIPO_CAPUCHA, ROJO_JERSEY, 55f, true),
            itemNuevo(TIPO_PUNOS_CINTURA, ROJO_RIBB, 60f, false), itemNuevo(TIPO_PUNOS_CINTURA, BLANCO_RIBB, 60f, false));

        assertThat(gramos(buzo)).containsExactly(55f, 30f, 30f);
    }

    @Test
    void filaDelMismoTipoConOtraTela_recibeElSugeridoCompletoDeLaSuya() {
        Producto buzo = productoGuardado("Buzo");

        guardar(itemNuevo(TIPO_PUNOS_CINTURA, ROJO_RIBB, 1f, false), itemNuevo(TIPO_PUNOS_CINTURA, BLANCO_RIBB, 1f, false),
            itemNuevo(TIPO_PUNOS_CINTURA, ROJO_JERSEY, 1f, false));

        assertThat(gramos(buzo)).containsExactly(30f, 30f, 70f);
    }

    @Test
    void alCambiarLaTelaDeUnaFilaSugerida_tomaElSugeridoDeLaNueva() {
        Producto buzo = productoGuardado("Buzo");
        guardar(itemNuevo(TIPO_CAPUCHA, ROJO_JERSEY, 70f, false));
        ProductoInsumoSecundario fila = buzo.getInsumosSecundarios().get(0);
        fila.setId(20L);

        guardar(new ProductoInsumoSecundarioItemRequest(20L, TIPO_CAPUCHA.getId(), null, RIBB.getId(), ROJO_RIBB.getId(), 70f, false));

        assertThat(fila.getTipoTela()).isSameAs(RIBB);
        assertThat(fila.getCantidad()).isEqualTo(60f);
    }

    @Test
    void insumoLibreYTelaSinSugerido_noSeReparten() {
        Producto buzo = productoGuardado("Buzo");

        guardar(item(null, "Bolsillo", ROJO_FRIZA, 20f), item(null, "Bolsillo", ROJO_FRIZA, 15f),
            itemNuevo(TIPO_CAPUCHA, ROJO_FRIZA, 80f, false), itemNuevo(TIPO_CAPUCHA, ROJO_FRIZA, 90f, false));

        assertThat(gramos(buzo)).containsExactly(20f, 15f, 80f, 90f);
    }

    /** Sin el indicador en el pedido (frontend anterior): cambiar los gramos de una fila
     *  existente la marca como editada a mano; no tocarlos conserva lo que tenía. */
    @Test
    void sinIndicadorEnElPedido_unCambioDeGramosCuentaComoEdicionManual() {
        Producto buzo = buzoConPunosAsignados();
        ProductoInsumoSecundario filaCapucha = buzo.getInsumosSecundarios().get(0);
        ProductoInsumoSecundario punosRojos = buzo.getInsumosSecundarios().get(1);
        ProductoInsumoSecundario punosBlancos = buzo.getInsumosSecundarios().get(2);

        guardar(
            itemExistente(filaCapucha, TIPO_CAPUCHA, ROJO_JERSEY, 70f),
            itemExistente(punosRojos, TIPO_PUNOS_CINTURA, ROJO_RIBB, 45f),
            itemExistente(punosBlancos, TIPO_PUNOS_CINTURA, BLANCO_RIBB, 30f));

        assertThat(filaCapucha.getCantidadEditadaManualmente()).isFalse();
        assertThat(punosRojos.getCantidadEditadaManualmente()).isTrue();
        assertThat(punosBlancos.getCantidadEditadaManualmente()).isFalse();
        assertThat(gramos(buzo)).containsExactly(70f, 45f, 30f);
    }

    /** Los tipos que se cortan (su tela sugerida es por peso) no aceptan una tela por unidad. */
    @Test
    void capuchaOPunosEnUnaTelaPorUnidad_seRechaza_peroElCierreSiLaUsa() {
        Producto campera = productoGuardado("Campera");

        assertThatThrownBy(() -> guardar(itemNuevo(TIPO_CAPUCHA, ROJO_CIERRE, 1f, true)))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("\"Capucha\" se corta en tela: no puede usar Cierre");
        assertThatThrownBy(() -> guardar(itemNuevo(TIPO_PUNOS_CINTURA, ROJO_CIERRE, 1f, true)))
            .isInstanceOf(BusinessRuleException.class);

        guardar(itemNuevo(TIPO_CIERRE, ROJO_CIERRE, 1f, false), item(null, "Tirador", ROJO_CIERRE, 1f));
        assertThat(campera.getInsumosSecundarios()).hasSize(2);
    }

    // ---------- Sugerencia automática al marcar los colores ----------

    private void marcarColores(Producto producto) {
        Long idPosicion = producto.getPatronCorte().getColores().get(0).getId();
        service.asignarColores(ID_PRODUCTO,
            new ProductoColoresRequest(List.of(new ProductoColorItemRequest(idPosicion, ROJO_FRIZA.getId(), null, null, null))),
            ID_ACTOR);
    }

    @Test
    void marcarColoresDeUnaCampera_sugiereCierreCapuchaYPunosConSuTipo() {
        Producto campera = productoGuardado("Campera");

        marcarColores(campera);

        assertThat(campera.getInsumosSecundarios())
            .extracting(ProductoInsumoSecundario::getTipoInsumo, ProductoInsumoSecundario::getDescripcion,
                ProductoInsumoSecundario::getColor, ProductoInsumoSecundario::getCantidad)
            .containsExactly(
                tuple(TIPO_CIERRE, "Cierre", ROJO_CIERRE, 1f),
                tuple(TIPO_CAPUCHA, "Capucha", ROJO_JERSEY, 70f),
                tuple(TIPO_PUNOS_CINTURA, "Puños y cintura", ROJO_RIBB, 60f));
        assertThat(ProductoDisenoValidador.motivoDisenoIncompleto(campera)).isEmpty();
    }

    @Test
    void marcarColores_noPisaUnInsumoDeEseTipoYaCargado() {
        Producto buzo = productoGuardado("Buzo");
        ProductoInsumoSecundario punosElegidos = InsumosDePrueba.punosYCintura(BLANCO_RIBB, 45f);
        agregar(buzo, punosElegidos);

        marcarColores(buzo);

        assertThat(buzo.getInsumosSecundarios()).hasSize(2);
        assertThat(punosElegidos.getColor()).isSameAs(BLANCO_RIBB);
        assertThat(punosElegidos.getCantidad()).isEqualTo(45f);
    }

    /** Un insumo libre (sin tipo) no cuenta como ninguno de los insumos exigidos. */
    @Test
    void marcarColores_unInsumoLibreNoImpideLaSugerencia() {
        Producto buzo = productoGuardado("Buzo");
        agregar(buzo, libre("Bolsillo", FRIZA, ROJO_FRIZA, 20f));

        marcarColores(buzo);

        assertThat(buzo.getInsumosSecundarios())
            .extracting(ProductoInsumoSecundario::getTipoInsumo)
            .containsExactly(null, TIPO_CAPUCHA, TIPO_PUNOS_CINTURA);
    }
}
