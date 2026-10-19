package com.weclover.backend.service;

import static com.weclover.backend.service.InsumosDePrueba.CIERRE;
import static com.weclover.backend.service.InsumosDePrueba.FRIZA;
import static com.weclover.backend.service.InsumosDePrueba.JERSEY;
import static com.weclover.backend.service.InsumosDePrueba.RIBB;
import static com.weclover.backend.service.InsumosDePrueba.TIPO_CAPUCHA;
import static com.weclover.backend.service.InsumosDePrueba.TIPO_CIERRE;
import static com.weclover.backend.service.InsumosDePrueba.TIPO_PUNOS_CINTURA;
import static com.weclover.backend.service.InsumosDePrueba.agregar;
import static com.weclover.backend.service.InsumosDePrueba.asignar;
import static com.weclover.backend.service.InsumosDePrueba.capucha;
import static com.weclover.backend.service.InsumosDePrueba.cierre;
import static com.weclover.backend.service.InsumosDePrueba.color;
import static com.weclover.backend.service.InsumosDePrueba.libre;
import static com.weclover.backend.service.InsumosDePrueba.pieza;
import static com.weclover.backend.service.InsumosDePrueba.pin;
import static com.weclover.backend.service.InsumosDePrueba.productoConDisenoCargado;
import static com.weclover.backend.service.InsumosDePrueba.punosYCintura;
import static com.weclover.backend.service.InsumosDePrueba.tela;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.PatronCortePosicionPieza;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoInsumoSecundario;
import com.weclover.backend.service.ResolucionTelaPin.Motivo;
import com.weclover.backend.service.ResolucionTelaPin.Resultado;

/** Tela y color de cada pin de la moldería de un producto: todos los casos. */
class ResolucionTelaPinTest {

    private static final PaletaColores ROJO_FRIZA = color(1L, "Rojo", FRIZA);
    private static final PaletaColores AZUL_FRIZA = color(6L, "Azul", FRIZA);
    private static final PaletaColores ROJO_JERSEY = color(2L, "Rojo", JERSEY);
    private static final PaletaColores ROJO_RIBB = color(3L, "Rojo", RIBB);
    private static final PaletaColores BLANCO_RIBB = color(4L, "Blanco", RIBB);
    private static final PaletaColores ROJO_CIERRE = color(5L, "Rojo", CIERRE);

    private Producto buzo;
    private PatronCortePosicionPieza pinEspalda;
    private PatronCortePosicionPieza pinMangaPosicion2;
    private PatronCortePosicionPieza pinCapucha;
    private PatronCortePosicionPieza pinPuno;
    private PatronCortePosicionPieza pinCintura;

    @BeforeEach
    void setUp() {
        // Dos posiciones de color: la 1 en rojo y la 2 en azul.
        buzo = productoConDisenoCargado(1L, "Buzo", 10, ROJO_FRIZA, 300, 300);
        buzo.getColores().get(1).setPaletaColor(AZUL_FRIZA);
        pinEspalda = pin(100L, buzo, 0, pieza(10L, "Espalda", null), null);
        pinMangaPosicion2 = pin(101L, buzo, 1, pieza(11L, "Manga", null), null);
        pinCapucha = pin(103L, buzo, 0, pieza(12L, "Capucha", TIPO_CAPUCHA), null);
        pinPuno = pin(104L, buzo, 0, pieza(13L, "Puño", TIPO_PUNOS_CINTURA), null);
        pinCintura = pin(106L, buzo, 1, pieza(14L, "Cintura", TIPO_PUNOS_CINTURA), null);
    }

    private static void assertPendiente(Resultado resultado, Motivo motivo) {
        assertThat(resultado.resuelto()).isFalse();
        assertThat(resultado.motivo()).isEqualTo(motivo);
        assertThat(resultado.tela()).isNull();
        assertThat(resultado.color()).isNull();
    }

    // ---------- Piezas del cuerpo ----------

    @Test
    void piezaDelCuerpo_tomaLaTelaDelProductoYElColorDeSuPosicion() {
        Resultado espalda = ResolucionTelaPin.resolver(buzo, pinEspalda);
        Resultado manga = ResolucionTelaPin.resolver(buzo, pinMangaPosicion2);

        assertThat(espalda.resuelto()).isTrue();
        assertThat(espalda.tela()).isSameAs(FRIZA);
        assertThat(espalda.color()).isSameAs(ROJO_FRIZA);
        assertThat(espalda.insumo()).isNull();
        assertThat(manga.tela()).isSameAs(FRIZA);
        assertThat(manga.color()).isSameAs(AZUL_FRIZA);
    }

    @Test
    void piezaDelCuerpo_sinTelaOSinColorMarcadoEnSuPosicion_quedaPendiente() {
        buzo.getColores().remove(1);
        assertPendiente(ResolucionTelaPin.resolver(buzo, pinMangaPosicion2), Motivo.CUERPO_INCOMPLETO);
        assertThat(ResolucionTelaPin.resolver(buzo, pinEspalda).resuelto()).isTrue();

        buzo.setTipoTela(null);
        assertPendiente(ResolucionTelaPin.resolver(buzo, pinEspalda), Motivo.CUERPO_INCOMPLETO);
    }

    @Test
    void piezaDelCuerpo_noMiraLosInsumos() {
        agregar(buzo, libre("Bolsillo", RIBB, ROJO_RIBB, 20f));

        assertThat(ResolucionTelaPin.resolver(buzo, pinEspalda).tela()).isSameAs(FRIZA);
    }

    // ---------- Piezas con tipo de insumo ----------

    @Test
    void sinNingunaFilaDeEseTipo_faltaInsumo() {
        agregar(buzo, punosYCintura(ROJO_RIBB, 60f), libre("Capucha forrada", JERSEY, ROJO_JERSEY, 70f));

        assertPendiente(ResolucionTelaPin.resolver(buzo, pinCapucha), Motivo.FALTA_INSUMO);
    }

    @Test
    void conUnaSolaFila_valeParaTodosLosPinesDelTipo_sinAsignar() {
        ProductoInsumoSecundario punos = punosYCintura(BLANCO_RIBB, 60f);
        ProductoInsumoSecundario filaCapucha = capucha(ROJO_JERSEY, 70f);
        agregar(buzo, filaCapucha, punos);

        Resultado puno = ResolucionTelaPin.resolver(buzo, pinPuno);
        Resultado cintura = ResolucionTelaPin.resolver(buzo, pinCintura);
        Resultado capuchaResuelta = ResolucionTelaPin.resolver(buzo, pinCapucha);

        assertThat(puno.tela()).isSameAs(RIBB);
        assertThat(puno.color()).isSameAs(BLANCO_RIBB);
        assertThat(puno.insumo()).isSameAs(punos);
        assertThat(cintura.color()).isSameAs(BLANCO_RIBB);
        assertThat(capuchaResuelta.tela()).isSameAs(JERSEY);
        assertThat(capuchaResuelta.color()).isSameAs(ROJO_JERSEY);
        assertThat(capuchaResuelta.insumo()).isSameAs(filaCapucha);
    }

    @Test
    void conVariasFilas_cadaPinTomaLaFilaQueTieneAsignada() {
        ProductoInsumoSecundario rojos = punosYCintura(ROJO_RIBB, 30f);
        ProductoInsumoSecundario blancos = punosYCintura(BLANCO_RIBB, 30f);
        agregar(buzo, rojos, blancos);
        asignar(rojos, pinPuno);
        asignar(blancos, pinCintura);

        assertThat(ResolucionTelaPin.resolver(buzo, pinPuno).color()).isSameAs(ROJO_RIBB);
        assertThat(ResolucionTelaPin.resolver(buzo, pinPuno).insumo()).isSameAs(rojos);
        assertThat(ResolucionTelaPin.resolver(buzo, pinCintura).color()).isSameAs(BLANCO_RIBB);
    }

    @Test
    void conVariasFilasYElPinSinAsignar_sinAsignar() {
        ProductoInsumoSecundario rojos = punosYCintura(ROJO_RIBB, 30f);
        agregar(buzo, rojos, punosYCintura(BLANCO_RIBB, 30f));
        asignar(rojos, pinPuno);

        assertPendiente(ResolucionTelaPin.resolver(buzo, pinCintura), Motivo.SIN_ASIGNAR);
        assertThat(ResolucionTelaPin.resolver(buzo, pinPuno).resuelto()).isTrue();
    }

    /** Aunque las dos filas sean del mismo color, no se elige una por el pin: hay que asignar. */
    @Test
    void conVariasFilasIguales_tampocoSeAsume() {
        agregar(buzo, punosYCintura(ROJO_RIBB, 30f), punosYCintura(ROJO_RIBB, 30f));

        assertPendiente(ResolucionTelaPin.resolver(buzo, pinPuno), Motivo.SIN_ASIGNAR);
    }

    @Test
    void filaSinTelaOSinColor_insumoIncompleto() {
        ProductoInsumoSecundario sinColor = capucha(null, 70f);
        agregar(buzo, sinColor);
        Resultado resultado = ResolucionTelaPin.resolver(buzo, pinCapucha);
        assertPendiente(resultado, Motivo.INSUMO_INCOMPLETO);
        assertThat(resultado.insumo()).isSameAs(sinColor);

        sinColor.setColor(ROJO_JERSEY);
        sinColor.setTipoTela(null);
        assertPendiente(ResolucionTelaPin.resolver(buzo, pinCapucha), Motivo.INSUMO_INCOMPLETO);
    }

    @Test
    void filaConTelaQueNoEsPorPeso_telaNoCortable() {
        ProductoInsumoSecundario capuchaEnCierre = capucha(ROJO_CIERRE, 1f);
        capuchaEnCierre.setTipoTela(CIERRE);
        agregar(buzo, capuchaEnCierre);

        assertPendiente(ResolucionTelaPin.resolver(buzo, pinCapucha), Motivo.TELA_NO_CORTABLE);
    }

    /** Una pieza marcada con el tipo Cierre nunca se puede cortar: su insumo es por unidad. */
    @Test
    void piezaDeTipoCierre_telaNoCortable() {
        PatronCortePosicionPieza pinCierre = pin(107L, buzo, 0, pieza(15L, "Tapa cierre", TIPO_CIERRE), null);
        agregar(buzo, cierre(ROJO_CIERRE));

        assertPendiente(ResolucionTelaPin.resolver(buzo, pinCierre), Motivo.TELA_NO_CORTABLE);
    }

    @Test
    void telaDelCuerpoQueNoEsPorPeso_telaNoCortable() {
        buzo.setTipoTela(tela(9L, "VINILO", "Vinilo", false, true, null));

        assertPendiente(ResolucionTelaPin.resolver(buzo, pinEspalda), Motivo.TELA_NO_CORTABLE);
    }

    /** Una asignación que quedó apuntando a una fila de otro tipo no cuenta. */
    @Test
    void asignacionAUnaFilaDeOtroTipo_seIgnora() {
        ProductoInsumoSecundario filaCapucha = capucha(ROJO_JERSEY, 70f);
        agregar(buzo, filaCapucha, punosYCintura(ROJO_RIBB, 30f), punosYCintura(BLANCO_RIBB, 30f));
        asignar(filaCapucha, pinPuno);

        assertPendiente(ResolucionTelaPin.resolver(buzo, pinPuno), Motivo.SIN_ASIGNAR);
    }
}
