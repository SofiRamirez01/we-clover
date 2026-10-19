package com.weclover.backend.service;

import static com.weclover.backend.service.InsumosDePrueba.CIERRE;
import static com.weclover.backend.service.InsumosDePrueba.FRIZA;
import static com.weclover.backend.service.InsumosDePrueba.JERSEY;
import static com.weclover.backend.service.InsumosDePrueba.RIBB;
import static com.weclover.backend.service.InsumosDePrueba.TIPO_CAPUCHA;
import static com.weclover.backend.service.InsumosDePrueba.TIPO_PUNOS_CINTURA;
import static com.weclover.backend.service.InsumosDePrueba.agregar;
import static com.weclover.backend.service.InsumosDePrueba.capucha;
import static com.weclover.backend.service.InsumosDePrueba.color;
import static com.weclover.backend.service.InsumosDePrueba.pieza;
import static com.weclover.backend.service.InsumosDePrueba.pin;
import static com.weclover.backend.service.InsumosDePrueba.productoConDisenoCargado;
import static com.weclover.backend.service.InsumosDePrueba.punosYCintura;
import static com.weclover.backend.service.InsumosDePrueba.tela;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.weclover.backend.config.CorteProperties;
import com.weclover.backend.dto.corte.GrupoTelaCorteResponse;
import com.weclover.backend.dto.corte.ItemCorteResponse;
import com.weclover.backend.dto.corte.MotivoPendienteCorte;
import com.weclover.backend.dto.corte.PendienteCorteResponse;
import com.weclover.backend.dto.corte.PreparacionCorteResponse;
import com.weclover.backend.dto.corte.TipoFaltanteCorte;
import com.weclover.backend.entity.AlumnoProductoTalle;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.GrupoTalle;
import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.PatronCorte;
import com.weclover.backend.entity.PatronCorteColor;
import com.weclover.backend.entity.PatronCortePosicionPieza;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Pieza;
import com.weclover.backend.entity.PiezaTalle;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoInsumoSecundario;
import com.weclover.backend.entity.TablaTalle;
import com.weclover.backend.entity.Tanda;
import com.weclover.backend.exception.ForbiddenException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.repository.AlumnoProductoTalleRepository;
import com.weclover.backend.repository.PatronCorteRepository;
import com.weclover.backend.repository.PiezaTalleRepository;
import com.weclover.backend.repository.ProductoInsumoSecundarioRepository;
import com.weclover.backend.repository.ProductoRepository;
import com.weclover.backend.repository.TandaRepository;

import tools.jackson.databind.json.JsonMapper;

/** Preparación del corte de una tanda (Módulo 4, paso 2): demanda de piezas y pendientes. */
class PreparacionCorteServiceTest {

    private static final Long ID_TANDA = 1L;
    private static final Long ID_ACTOR = 14L;

    private static final GrupoTalle GRUPO = GrupoTalle.builder().id(1L).nombre("Campera/Buzo").build();
    private static final TablaTalle T1 = TablaTalle.builder().id(11L).grupoTalle(GRUPO).talle("1").orden(1).anchoCm(40).largoCm(50).build();
    private static final TablaTalle T2 = TablaTalle.builder().id(12L).grupoTalle(GRUPO).talle("2").orden(2).anchoCm(45).largoCm(55).build();

    private static final PaletaColores ROJO_FRIZA = color(1L, "Rojo", FRIZA);
    private static final PaletaColores BLANCO_FRIZA = color(2L, "Blanco", FRIZA);
    private static final PaletaColores AZUL_FRIZA = color(3L, "Azul", FRIZA);
    private static final PaletaColores ROJO_JERSEY = color(4L, "Rojo", JERSEY);
    private static final PaletaColores ROJO_RIBB = color(5L, "Rojo", RIBB);
    private static final PaletaColores BLANCO_RIBB = color(6L, "Blanco", RIBB);
    private static final PaletaColores ROJO_CIERRE = color(7L, "Rojo", CIERRE);

    private final TandaRepository tandaRepository = mock(TandaRepository.class);
    private final ProductoRepository productoRepository = mock(ProductoRepository.class);
    private final ProductoInsumoSecundarioRepository productoInsumoSecundarioRepository = mock(ProductoInsumoSecundarioRepository.class);
    private final PatronCorteRepository patronCorteRepository = mock(PatronCorteRepository.class);
    private final PiezaTalleRepository piezaTalleRepository = mock(PiezaTalleRepository.class);
    private final AlumnoProductoTalleRepository alumnoProductoTalleRepository = mock(AlumnoProductoTalleRepository.class);
    private final AutorizacionService autorizacionService = mock(AutorizacionService.class);

    private final PreparacionCorteService service = new PreparacionCorteService(
        tandaRepository, productoRepository, productoInsumoSecundarioRepository, patronCorteRepository,
        piezaTalleRepository, alumnoProductoTalleRepository, autorizacionService,
        new CorteProperties(90, 800, 0.5, 20, 2), JsonMapper.builder().build());

    /** Lo que devolverían las consultas en lote: se arma con los helpers de cada test. */
    private final List<Producto> productos = new ArrayList<>();
    private final List<PiezaTalle> geometrias = new ArrayList<>();
    private final List<AlumnoProductoTalle> unidades = new ArrayList<>();

    private long siguienteId = 1000;

    @BeforeEach
    void setUp() {
        lenient().when(tandaRepository.findById(ID_TANDA))
            .thenReturn(Optional.of(Tanda.builder().id(ID_TANDA).nombre("A").build()));
        lenient().when(productoRepository.buscarParaCorte(ID_TANDA)).thenReturn(productos);
        lenient().when(patronCorteRepository.cargarPines(anyCollection())).thenAnswer(inv -> productos.stream()
            .map(Producto::getPatronCorte).filter(p -> p != null).distinct()
            .flatMap(p -> p.getColores().stream()).toList());
        lenient().when(piezaTalleRepository.buscarConTallePorPiezas(anyCollection())).thenReturn(geometrias);
        lenient().when(alumnoProductoTalleRepository.buscarConTallePorProductos(anyCollection())).thenReturn(unidades);
    }

    // ---------- Armado de datos ----------

    /** Producto de un pedido de la tanda, con tela Friza, moldería de una posición por cada
     *  color dado y esos colores ya marcados. Todavía sin pines ni unidades. */
    private Producto producto(String tipoPrenda, int cantidad, EstadoPedido estado, PaletaColores... coloresPorPosicion) {
        long id = siguienteId++;
        Producto producto = productoConDisenoCargado(id, tipoPrenda, cantidad, coloresPorPosicion[0], new int[coloresPorPosicion.length]);
        for (int i = 1; i < coloresPorPosicion.length; i++) {
            producto.getColores().get(i).setPaletaColor(coloresPorPosicion[i]);
        }
        PatronCorte molderia = producto.getPatronCorte();
        molderia.setId(id + 5000);
        molderia.setNumeroInterno(1);
        for (int i = 0; i < molderia.getColores().size(); i++) {
            molderia.getColores().get(i).setId(id * 10 + i);
        }
        producto.getTipoPrenda().setGrupoTalle(GRUPO);
        producto.setPedido(Pedido.builder().id(id + 9000).codigoInterno("2026-" + id).estadoActual(estado).build());
        productos.add(producto);
        return producto;
    }

    private Producto buzo(int cantidad, PaletaColores... coloresPorPosicion) {
        return producto("Buzo", cantidad, EstadoPedido.EN_PRODUCCION, coloresPorPosicion);
    }

    /** Agrega un pin de esa pieza en la posición indicada (0 = la primera). */
    private PatronCortePosicionPieza conPin(Producto producto, int posicion, Pieza pieza) {
        return pin(siguienteId++, producto, posicion, pieza, null);
    }

    /** Unidades medidas con ese talle. */
    private void unidades(Producto producto, TablaTalle talle, int cantidad) {
        for (int i = 0; i < cantidad; i++) {
            unidades.add(AlumnoProductoTalle.builder().id(siguienteId++).producto(producto)
                .anchoCm(talle.getAnchoCm()).largoCm(talle.getLargoCm()).talleAsignado(talle).personalizado(false).build());
        }
    }

    private void unidadesPersonalizadas(Producto producto, int cantidad) {
        for (int i = 0; i < cantidad; i++) {
            unidades.add(AlumnoProductoTalle.builder().id(siguienteId++).producto(producto)
                .anchoCm(99).largoCm(99).talleAsignado(null).personalizado(true).build());
        }
    }

    /** Geometría de esa pieza en esos talles. */
    private void geometria(Pieza pieza, TablaTalle... talles) {
        for (TablaTalle talle : talles) {
            geometrias.add(PiezaTalle.builder().id(siguienteId++).pieza(pieza).talle(talle)
                .coordenadasJson("[[0.0,0.0],[10.0,0.0],[10.0,20.0]]").anchoCm(10).largoCm(20).areaCm2(100).build());
        }
    }

    private Pieza piezaConGeometria(Long id, String nombre, com.weclover.backend.entity.TipoInsumo tipo) {
        Pieza pieza = pieza(id, nombre, tipo);
        geometria(pieza, T1, T2);
        return pieza;
    }

    private PreparacionCorteResponse preparar() {
        return service.preparar(ID_TANDA, ID_ACTOR);
    }

    private static GrupoTelaCorteResponse grupo(PreparacionCorteResponse respuesta, String codigoTela) {
        return respuesta.grupos().stream().filter(g -> g.codigoTipoTela().equals(codigoTela)).findFirst().orElseThrow();
    }

    private static ItemCorteResponse item(PreparacionCorteResponse respuesta, String codigoTela, Pieza pieza, TablaTalle talle) {
        return grupo(respuesta, codigoTela).items().stream()
            .filter(i -> i.idPieza().equals(pieza.getId()) && i.idTalle().equals(talle.getId()))
            .findFirst().orElseThrow();
    }

    private static List<PendienteCorteResponse> pendientesDe(PreparacionCorteResponse respuesta, Producto producto) {
        return respuesta.pendientes().stream().filter(p -> p.idProducto().equals(producto.getId())).toList();
    }

    private static boolean apareceEnLaDemanda(PreparacionCorteResponse respuesta, Producto producto) {
        return respuesta.grupos().stream().flatMap(g -> g.items().stream()).flatMap(i -> i.origen().stream())
            .anyMatch(o -> o.idProducto().equals(producto.getId()));
    }

    // ---------- Demanda ----------

    @Test
    void piezasIgualesDeDistintoColor_seUnificanEnUnSoloItem_conUnaCapaPorColor() {
        Pieza espalda = piezaConGeometria(1L, "Espalda", null);
        Producto rojo = buzo(10, ROJO_FRIZA);
        Producto blanco = buzo(10, BLANCO_FRIZA);
        conPin(rojo, 0, espalda);
        conPin(blanco, 0, espalda);
        unidades(rojo, T2, 10);
        unidades(blanco, T2, 10);

        PreparacionCorteResponse respuesta = preparar();

        assertThat(respuesta.grupos()).singleElement().satisfies(g -> assertThat(g.items()).hasSize(1));
        ItemCorteResponse espaldaT2 = item(respuesta, "FRIZA", espalda, T2);
        assertThat(espaldaT2.porColor()).extracting(c -> c.nombreColor(), c -> c.cantidad())
            .containsExactly(tuple("Blanco", 10), tuple("Rojo", 10));
        assertThat(espaldaT2.cantidadTotal()).isEqualTo(20);
        assertThat(espaldaT2.origen()).extracting(o -> o.idProducto(), o -> o.idPaletaColor(), o -> o.cantidad(), o -> o.idProductoInsumo())
            .containsExactlyInAnyOrder(tuple(rojo.getId(), ROJO_FRIZA.getId(), 10, null), tuple(blanco.getId(), BLANCO_FRIZA.getId(), 10, null));
        assertThat(respuesta.resumen().unidadesIncluidas()).isEqualTo(20);
        assertThat(respuesta.resumen().unidadesPendientes()).isZero();
    }

    @Test
    void cantidades_sonLasUnidadesDeCadaTalle() {
        Pieza espalda = piezaConGeometria(1L, "Espalda", null);
        Producto buzo = buzo(5, ROJO_FRIZA);
        conPin(buzo, 0, espalda);
        unidades(buzo, T1, 3);
        unidades(buzo, T2, 2);

        PreparacionCorteResponse respuesta = preparar();

        assertThat(grupo(respuesta, "FRIZA").items()).extracting(ItemCorteResponse::talle, ItemCorteResponse::cantidadTotal)
            .containsExactly(tuple("1", 3), tuple("2", 2));
        assertThat(respuesta.piezas()).singleElement().satisfies(p -> {
            assertThat(p.id()).isEqualTo(1L);
            assertThat(p.talles()).extracting(t -> t.talle()).containsExactly("1", "2");
            assertThat(p.talles().get(0).coordenadas()).hasSize(3);
            assertThat(p.talles().get(0).coordenadas().get(2)).containsExactly(10.0, 20.0);
        });
    }

    @Test
    void piezaConDosPines_cuentaDosInstanciasPorPrenda() {
        Pieza manga = piezaConGeometria(2L, "Manga", null);
        Producto buzo = buzo(3, ROJO_FRIZA);
        conPin(buzo, 0, manga);
        conPin(buzo, 0, manga);
        unidades(buzo, T2, 3);

        assertThat(item(preparar(), "FRIZA", manga, T2).cantidadTotal()).isEqualTo(6);
    }

    /** Una pieza del cuerpo en la posición 2 toma el color de esa posición, no el de la 1. */
    @Test
    void piezaDelCuerpo_tomaElColorDeLaPosicionDeSuPin() {
        Pieza espalda = piezaConGeometria(1L, "Espalda", null);
        Pieza manga = piezaConGeometria(2L, "Manga", null);
        Producto bicolor = buzo(4, ROJO_FRIZA, AZUL_FRIZA);
        conPin(bicolor, 0, espalda);
        conPin(bicolor, 1, manga);
        unidades(bicolor, T2, 4);

        PreparacionCorteResponse respuesta = preparar();

        assertThat(item(respuesta, "FRIZA", espalda, T2).porColor()).extracting(c -> c.nombreColor()).containsExactly("Rojo");
        assertThat(item(respuesta, "FRIZA", manga, T2).porColor()).extracting(c -> c.nombreColor()).containsExactly("Azul");
    }

    @Test
    void piezaLigadaAUnInsumo_tomaLaTelaYElColorDeSuFila_noLosDelCuerpo() {
        Pieza espalda = piezaConGeometria(1L, "Espalda", null);
        Pieza piezaCapucha = piezaConGeometria(3L, "Capucha", TIPO_CAPUCHA);
        Producto buzo = buzo(2, ROJO_FRIZA);
        ProductoInsumoSecundario filaCapucha = capucha(ROJO_JERSEY, 70f);
        filaCapucha.setId(77L);
        agregar(buzo, filaCapucha);
        conPin(buzo, 0, espalda);
        conPin(buzo, 0, piezaCapucha);
        unidades(buzo, T2, 2);

        PreparacionCorteResponse respuesta = preparar();

        assertThat(respuesta.grupos()).extracting(GrupoTelaCorteResponse::codigoTipoTela).containsExactly("FRIZA", "JERSEY");
        ItemCorteResponse capuchaT2 = item(respuesta, "JERSEY", piezaCapucha, T2);
        assertThat(capuchaT2.porColor()).extracting(c -> c.idPaletaColor(), c -> c.cantidad()).containsExactly(tuple(ROJO_JERSEY.getId(), 2));
        assertThat(capuchaT2.origen()).singleElement().satisfies(o -> assertThat(o.idProductoInsumo()).isEqualTo(77L));
        assertThat(grupo(respuesta, "FRIZA").items()).extracting(ItemCorteResponse::idPieza).containsExactly(espalda.getId());
    }

    @Test
    void conVariasFilasDelMismoTipo_cadaPinTomaLaFilaAsignada_yLosColoresSeUnifican() {
        Pieza cintura = piezaConGeometria(4L, "Cintura", TIPO_PUNOS_CINTURA);
        Producto buzo = buzo(3, ROJO_FRIZA);
        ProductoInsumoSecundario rojos = punosYCintura(ROJO_RIBB, 30f);
        ProductoInsumoSecundario blancos = punosYCintura(BLANCO_RIBB, 30f);
        agregar(buzo, rojos, blancos);
        PatronCortePosicionPieza pinA = conPin(buzo, 0, cintura);
        PatronCortePosicionPieza pinB = conPin(buzo, 0, cintura);
        InsumosDePrueba.asignar(rojos, pinA);
        InsumosDePrueba.asignar(blancos, pinB);
        unidades(buzo, T2, 3);

        ItemCorteResponse cinturaT2 = item(preparar(), "RIBB", cintura, T2);

        assertThat(cinturaT2.porColor()).extracting(c -> c.nombreColor(), c -> c.cantidad())
            .containsExactly(tuple("Blanco", 3), tuple("Rojo", 3));
        assertThat(cinturaT2.cantidadTotal()).isEqualTo(6);
    }

    @Test
    void simetrica_viajaComoDato_yNoCambiaLaCantidad() {
        Pieza espalda = piezaConGeometria(1L, "Espalda", null);
        espalda.setSimetrica(true);
        Producto buzo = buzo(4, ROJO_FRIZA);
        conPin(buzo, 0, espalda);
        unidades(buzo, T2, 4);

        PreparacionCorteResponse respuesta = preparar();

        assertThat(respuesta.piezas()).singleElement().satisfies(p -> assertThat(p.simetrica()).isTrue());
        assertThat(item(respuesta, "FRIZA", espalda, T2).cantidadTotal()).isEqualTo(4);
    }

    @Test
    void parametros_salenDeLaConfiguracion() {
        assertThat(preparar().parametros()).satisfies(p -> {
            assertThat(p.anchoUtilCm()).isEqualTo(90);
            assertThat(p.largoMaximoCm()).isEqualTo(800);
            assertThat(p.margenCorteCm()).isEqualTo(0.5);
            assertThat(p.capasMaximas()).isEqualTo(20);
            assertThat(p.telasPorCapa()).isEqualTo(2);
        });
    }

    // ---------- Pines sin resolver: todo el producto queda pendiente ----------

    /** Buzo de 4 unidades con espalda (cuerpo) y un pin del tipo dado, ya medido en T2. */
    private Producto buzoConPinDeInsumo(Pieza piezaInsumo) {
        Pieza espalda = piezaConGeometria(1L, "Espalda", null);
        Producto buzo = buzo(4, ROJO_FRIZA);
        conPin(buzo, 0, espalda);
        conPin(buzo, 0, piezaInsumo);
        unidades(buzo, T2, 4);
        return buzo;
    }

    private void assertProductoPendienteCompletoPor(PreparacionCorteResponse respuesta, Producto producto, MotivoPendienteCorte motivo) {
        assertThat(pendientesDe(respuesta, producto)).singleElement().satisfies(p -> {
            assertThat(p.motivo()).isEqualTo(motivo);
            assertThat(p.unidadesAfectadas()).isEqualTo(producto.getCantidadTotal());
            assertThat(p.pines()).isNotEmpty();
            assertThat(p.pines()).allSatisfy(pin -> assertThat(pin.idPosicionPieza()).isNotNull());
        });
        assertThat(apareceEnLaDemanda(respuesta, producto)).isFalse();
        assertThat(respuesta.resumen().unidadesPendientes()).isEqualTo(producto.getCantidadTotal());
        assertThat(respuesta.resumen().unidadesIncluidas()).isZero();
    }

    @Test
    void faltaInsumo_dejaPendienteTodoElProducto() {
        Producto buzo = buzoConPinDeInsumo(piezaConGeometria(3L, "Capucha", TIPO_CAPUCHA));

        assertProductoPendienteCompletoPor(preparar(), buzo, MotivoPendienteCorte.FALTA_INSUMO);
    }

    @Test
    void sinAsignar_dejaPendienteTodoElProducto_yPideAsignarLosPines() {
        Producto buzo = buzoConPinDeInsumo(piezaConGeometria(4L, "Cintura", TIPO_PUNOS_CINTURA));
        agregar(buzo, punosYCintura(ROJO_RIBB, 30f), punosYCintura(BLANCO_RIBB, 30f));

        PreparacionCorteResponse respuesta = preparar();

        assertProductoPendienteCompletoPor(respuesta, buzo, MotivoPendienteCorte.SIN_ASIGNAR);
        assertThat(respuesta.resumen().faltaCargar()).singleElement().satisfies(f -> {
            assertThat(f.tipo()).isEqualTo(TipoFaltanteCorte.PIN_SIN_ASIGNAR);
            assertThat(f.idReferencia()).isEqualTo(buzo.getId());
            assertThat(f.unidadesAfectadas()).isEqualTo(4);
        });
    }

    @Test
    void insumoIncompleto_dejaPendienteTodoElProducto() {
        Producto buzo = buzoConPinDeInsumo(piezaConGeometria(3L, "Capucha", TIPO_CAPUCHA));
        agregar(buzo, capucha(null, 70f));

        assertProductoPendienteCompletoPor(preparar(), buzo, MotivoPendienteCorte.INSUMO_INCOMPLETO);
    }

    @Test
    void telaNoCortable_dejaPendienteTodoElProducto() {
        Producto buzo = buzoConPinDeInsumo(piezaConGeometria(3L, "Capucha", TIPO_CAPUCHA));
        ProductoInsumoSecundario enCierre = capucha(ROJO_CIERRE, 1f);
        enCierre.setTipoTela(CIERRE);
        agregar(buzo, enCierre);

        assertProductoPendienteCompletoPor(preparar(), buzo, MotivoPendienteCorte.TELA_NO_CORTABLE);
    }

    /** Las dos posiciones tienen "un" color cada una por cantidad, pero la 2 quedó sin el suyo. */
    @Test
    void cuerpoIncompleto_dejaPendienteTodoElProducto() {
        Pieza espalda = piezaConGeometria(1L, "Espalda", null);
        Pieza manga = piezaConGeometria(2L, "Manga", null);
        Producto bicolor = buzo(4, ROJO_FRIZA, AZUL_FRIZA);
        PatronCorteColor posicionUno = bicolor.getPatronCorte().getColores().get(0);
        bicolor.getColores().get(1).setPatronCorteColor(posicionUno);
        conPin(bicolor, 0, espalda);
        conPin(bicolor, 1, manga);
        unidades(bicolor, T2, 4);

        assertProductoPendienteCompletoPor(preparar(), bicolor, MotivoPendienteCorte.CUERPO_INCOMPLETO);
    }

    @Test
    void telaDelCuerpoNoCortable_dejaPendienteTodoElProducto() {
        Pieza espalda = piezaConGeometria(1L, "Espalda", null);
        Producto buzo = buzo(4, ROJO_FRIZA);
        buzo.setTipoTela(tela(9L, "VINILO", "Vinilo", false, true, null));
        conPin(buzo, 0, espalda);
        unidades(buzo, T2, 4);

        assertProductoPendienteCompletoPor(preparar(), buzo, MotivoPendienteCorte.TELA_NO_CORTABLE);
    }

    // ---------- Otros pendientes ----------

    @Test
    void tallePersonalizado_dejaPendientesSoloEsasUnidades() {
        Pieza espalda = piezaConGeometria(1L, "Espalda", null);
        Producto buzo = buzo(4, ROJO_FRIZA);
        conPin(buzo, 0, espalda);
        unidades(buzo, T2, 3);
        unidadesPersonalizadas(buzo, 1);

        PreparacionCorteResponse respuesta = preparar();

        assertThat(pendientesDe(respuesta, buzo)).singleElement().satisfies(p -> {
            assertThat(p.motivo()).isEqualTo(MotivoPendienteCorte.TALLE_PERSONALIZADO);
            assertThat(p.unidadesAfectadas()).isEqualTo(1);
        });
        assertThat(item(respuesta, "FRIZA", espalda, T2).cantidadTotal()).isEqualTo(3);
        assertThat(respuesta.resumen().unidadesIncluidas()).isEqualTo(3);
        assertThat(respuesta.resumen().unidadesPendientes()).isEqualTo(1);
    }

    @Test
    void piezaSinGeometriaEnUnTalle_dejaPendientesLasUnidadesDeEseTalle_yElRestoEntra() {
        Pieza espalda = piezaConGeometria(1L, "Espalda", null);
        Pieza manga = pieza(2L, "Manga", null);
        geometria(manga, T2);
        Producto buzo = buzo(5, ROJO_FRIZA);
        conPin(buzo, 0, espalda);
        conPin(buzo, 0, manga);
        unidades(buzo, T1, 2);
        unidades(buzo, T2, 3);

        PreparacionCorteResponse respuesta = preparar();

        assertThat(pendientesDe(respuesta, buzo)).singleElement().satisfies(p -> {
            assertThat(p.motivo()).isEqualTo(MotivoPendienteCorte.PIEZA_SIN_TALLE);
            assertThat(p.talle()).isEqualTo("1");
            assertThat(p.unidadesAfectadas()).isEqualTo(2);
            assertThat(p.pines()).extracting(x -> x.nombrePieza()).containsExactly("Manga");
        });
        // Del talle 1 no se corta nada, ni siquiera la espalda (no se corta una prenda a medias).
        assertThat(grupo(respuesta, "FRIZA").items()).extracting(ItemCorteResponse::idPieza, ItemCorteResponse::talle)
            .containsExactlyInAnyOrder(tuple(1L, "2"), tuple(2L, "2"));
        assertThat(respuesta.resumen().unidadesIncluidas()).isEqualTo(3);
        assertThat(respuesta.resumen().unidadesPendientes()).isEqualTo(2);
        assertThat(respuesta.resumen().faltaCargar()).singleElement().satisfies(f -> {
            assertThat(f.tipo()).isEqualTo(TipoFaltanteCorte.PIEZA_SIN_TALLE_GRADUADO);
            assertThat(f.idReferencia()).isEqualTo(2L);
            assertThat(f.descripcion()).contains("talle 1");
            assertThat(f.unidadesAfectadas()).isEqualTo(2);
        });
    }

    @Test
    void posicionSinPines_dejaPendienteTodoElProducto_yPideCargarLaMolderia() {
        Pieza espalda = piezaConGeometria(1L, "Espalda", null);
        Producto bicolor = buzo(6, ROJO_FRIZA, AZUL_FRIZA);
        conPin(bicolor, 0, espalda);
        unidades(bicolor, T2, 6);

        PreparacionCorteResponse respuesta = preparar();

        assertThat(pendientesDe(respuesta, bicolor)).singleElement().satisfies(p -> {
            assertThat(p.motivo()).isEqualTo(MotivoPendienteCorte.POSICION_SIN_PINES);
            assertThat(p.unidadesAfectadas()).isEqualTo(6);
            assertThat(p.detalle()).contains("posición 2");
        });
        assertThat(respuesta.grupos()).isEmpty();
        assertThat(respuesta.resumen().faltaCargar()).singleElement().satisfies(f -> {
            assertThat(f.tipo()).isEqualTo(TipoFaltanteCorte.MOLDERIA_SIN_PINES);
            assertThat(f.idReferencia()).isEqualTo(bicolor.getPatronCorte().getId());
        });
    }

    @Test
    void talleIncompleto_dejaPendienteTodoElProducto() {
        Pieza espalda = piezaConGeometria(1L, "Espalda", null);
        Producto buzo = buzo(4, ROJO_FRIZA);
        conPin(buzo, 0, espalda);
        unidades(buzo, T2, 3);

        PreparacionCorteResponse respuesta = preparar();

        assertThat(pendientesDe(respuesta, buzo)).singleElement().satisfies(p -> {
            assertThat(p.motivo()).isEqualTo(MotivoPendienteCorte.TALLES_INCOMPLETOS);
            assertThat(p.unidadesAfectadas()).isEqualTo(4);
        });
        assertThat(respuesta.grupos()).isEmpty();
    }

    @Test
    void productoSinColores_quedaPendiente_yPideMarcarlos() {
        Pieza espalda = piezaConGeometria(1L, "Espalda", null);
        Producto buzo = buzo(4, ROJO_FRIZA);
        buzo.getColores().clear();
        conPin(buzo, 0, espalda);
        unidades(buzo, T2, 4);

        PreparacionCorteResponse respuesta = preparar();

        assertThat(pendientesDe(respuesta, buzo)).extracting(PendienteCorteResponse::motivo)
            .containsExactly(MotivoPendienteCorte.COLORES_INCOMPLETOS);
        assertThat(respuesta.resumen().faltaCargar()).extracting(f -> f.tipo()).containsExactly(TipoFaltanteCorte.PRODUCTO_SIN_COLORES);
    }

    /** Sin imagen de diseño ni insumos exigidos el producto entra igual: no se usa motivoDisenoIncompleto. */
    @Test
    void faltarImagenOInsumosExigidos_noMandaElProductoAPendientes() {
        Pieza espalda = piezaConGeometria(1L, "Espalda", null);
        Producto campera = producto("Campera", 2, EstadoPedido.SENADO, ROJO_FRIZA);
        campera.setImagenDisenoUrl(null);
        conPin(campera, 0, espalda);
        unidades(campera, T2, 2);

        PreparacionCorteResponse respuesta = preparar();

        assertThat(ProductoDisenoValidador.motivoDisenoIncompleto(campera)).isPresent();
        assertThat(respuesta.pendientes()).isEmpty();
        assertThat(item(respuesta, "FRIZA", espalda, T2).cantidadTotal()).isEqualTo(2);
    }

    @Test
    void variosMotivosEnUnProducto_unaEntradaPorMotivo_yLasUnidadesSeCuentanUnaVez() {
        Producto buzo = buzo(5, ROJO_FRIZA);
        buzo.setTipoTela(null);
        unidades(buzo, T2, 2);

        PreparacionCorteResponse respuesta = preparar();

        assertThat(pendientesDe(respuesta, buzo)).extracting(PendienteCorteResponse::motivo)
            .containsExactly(MotivoPendienteCorte.SIN_TELA, MotivoPendienteCorte.POSICION_SIN_PINES, MotivoPendienteCorte.TALLES_INCOMPLETOS);
        assertThat(respuesta.resumen().unidadesPendientes()).isEqualTo(5);
        assertThat(respuesta.resumen().pendientesPorMotivo()).extracting(m -> m.motivo(), m -> m.unidades())
            .containsExactly(tuple(MotivoPendienteCorte.SIN_TELA, 5), tuple(MotivoPendienteCorte.TALLES_INCOMPLETOS, 5),
                tuple(MotivoPendienteCorte.POSICION_SIN_PINES, 5));
        assertThat(respuesta.resumen().notaPendientesPorMotivo())
            .isEqualTo("Una unidad puede aparecer en más de un motivo; los totales por motivo no suman el total de pendientes.");
    }

    @Test
    void unPendiente_noFrenaAlResto() {
        Pieza espalda = piezaConGeometria(1L, "Espalda", null);
        Producto sinTela = buzo(3, ROJO_FRIZA);
        sinTela.setTipoTela(null);
        conPin(sinTela, 0, espalda);
        unidades(sinTela, T2, 3);
        Producto completo = buzo(4, BLANCO_FRIZA);
        conPin(completo, 0, espalda);
        unidades(completo, T2, 4);

        PreparacionCorteResponse respuesta = preparar();

        assertThat(pendientesDe(respuesta, sinTela)).extracting(PendienteCorteResponse::motivo).containsExactly(MotivoPendienteCorte.SIN_TELA);
        assertThat(item(respuesta, "FRIZA", espalda, T2).porColor()).extracting(c -> c.nombreColor(), c -> c.cantidad())
            .containsExactly(tuple("Blanco", 4));
        assertThat(respuesta.resumen().unidadesIncluidas()).isEqualTo(4);
        assertThat(respuesta.resumen().unidadesPendientes()).isEqualTo(3);
    }

    // ---------- Qué productos entran ----------

    @Test
    void pedidosEntregadosOCancelados_noEntran_yLosQueNoEstanListosSi() {
        Pieza espalda = piezaConGeometria(1L, "Espalda", null);
        for (EstadoPedido estado : List.of(EstadoPedido.ENTREGADO, EstadoPedido.CANCELADO, EstadoPedido.PRESUPUESTADO)) {
            Producto buzo = producto("Buzo", 2, estado, ROJO_FRIZA);
            conPin(buzo, 0, espalda);
            unidades(buzo, T2, 2);
        }

        PreparacionCorteResponse respuesta = preparar();

        assertThat(item(respuesta, "FRIZA", espalda, T2).origen()).singleElement()
            .satisfies(o -> assertThat(o.idProducto()).isEqualTo(productos.get(2).getId()));
        assertThat(respuesta.pendientes()).isEmpty();
        assertThat(respuesta.resumen().unidadesIncluidas()).isEqualTo(2);
    }

    @Test
    void bandera_noEntra() {
        Producto bandera = producto("Bandera", 1, EstadoPedido.EN_PRODUCCION, ROJO_FRIZA);
        bandera.getTipoPrenda().setGrupoTalle(null);

        PreparacionCorteResponse respuesta = preparar();

        assertThat(respuesta.pendientes()).isEmpty();
        assertThat(respuesta.grupos()).isEmpty();
        assertThat(respuesta.resumen().unidadesPendientes()).isZero();
    }

    // ---------- Permisos, 404 y carga en lote ----------

    @Test
    void lectura_permitidaAAdministrativoYPlanta() {
        preparar();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> roles = ArgumentCaptor.forClass(Set.class);
        verify(autorizacionService).verificarRolPermitido(eq(ID_ACTOR), roles.capture());
        assertThat(roles.getValue()).containsExactlyInAnyOrder("ROLE_ADMINISTRATIVO", "ROLE_PLANTA");
    }

    @Test
    void sinRolDeLectura_seRechazaSinConsultarNada() {
        doThrow(new ForbiddenException("sin permiso")).when(autorizacionService).verificarRolPermitido(eq(ID_ACTOR), anySet());

        assertThatThrownBy(this::preparar).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(tandaRepository, productoRepository, patronCorteRepository, piezaTalleRepository, alumnoProductoTalleRepository);
    }

    @Test
    void tandaInexistente_404() {
        assertThatThrownBy(() -> service.preparar(999L, ID_ACTOR))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("No existe la tanda con id 999");
        verify(productoRepository, never()).buscarParaCorte(anyLong());
    }

    /** Con varios productos, molderías y pines, cada consulta en lote se hace una sola vez y no
     *  hay ninguna otra consulta (ni por producto ni por pin). */
    @Test
    void cargaEnLote_unaConsultaPorTipoDeDato_sinImportarCuantosPinesHaya() {
        Pieza espalda = piezaConGeometria(1L, "Espalda", null);
        Pieza manga = piezaConGeometria(2L, "Manga", null);
        Pieza piezaCapucha = piezaConGeometria(3L, "Capucha", TIPO_CAPUCHA);
        for (int i = 0; i < 4; i++) {
            Producto buzo = buzo(3, i % 2 == 0 ? ROJO_FRIZA : BLANCO_FRIZA);
            agregar(buzo, capucha(ROJO_JERSEY, 70f));
            conPin(buzo, 0, espalda);
            conPin(buzo, 0, manga);
            conPin(buzo, 0, manga);
            conPin(buzo, 0, piezaCapucha);
            unidades(buzo, T2, 3);
        }

        PreparacionCorteResponse respuesta = preparar();

        assertThat(respuesta.resumen().unidadesIncluidas()).isEqualTo(12);
        verify(tandaRepository, times(1)).findById(ID_TANDA);
        verify(productoRepository, times(1)).buscarParaCorte(ID_TANDA);
        verify(productoRepository, times(1)).cargarColores(anyList());
        verify(productoRepository, times(1)).cargarInsumosSecundarios(anyList());
        verify(productoInsumoSecundarioRepository, times(1)).cargarAsignaciones(anyList());
        verify(patronCorteRepository, times(1)).cargarPosiciones(anyCollection());
        verify(patronCorteRepository, times(1)).cargarPines(anyCollection());
        verify(piezaTalleRepository, times(1)).buscarConTallePorPiezas(anyCollection());
        verify(alumnoProductoTalleRepository, times(1)).buscarConTallePorProductos(anyCollection());
        verifyNoMoreInteractions(tandaRepository, productoRepository, productoInsumoSecundarioRepository,
            patronCorteRepository, piezaTalleRepository, alumnoProductoTalleRepository);
    }

    @Test
    void tandaSinProductos_noHaceLasConsultasEnLote() {
        PreparacionCorteResponse respuesta = preparar();

        assertThat(respuesta.grupos()).isEmpty();
        assertThat(respuesta.resumen().unidadesIncluidas()).isZero();
        verify(productoRepository, never()).cargarColores(any());
        verifyNoInteractions(patronCorteRepository, piezaTalleRepository, alumnoProductoTalleRepository);
    }
}
