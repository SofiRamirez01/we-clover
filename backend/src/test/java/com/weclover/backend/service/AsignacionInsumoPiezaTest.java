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
import static com.weclover.backend.service.InsumosDePrueba.producto;
import static com.weclover.backend.service.InsumosDePrueba.productoConDisenoCargado;
import static com.weclover.backend.service.InsumosDePrueba.punosYCintura;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.PatronCortePosicionPieza;
import com.weclover.backend.entity.Pieza;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoInsumoSecundario;

/** Reglas de asignación de filas de insumo a pines de la moldería. */
class AsignacionInsumoPiezaTest {

    private static final PaletaColores ROJO_FRIZA = color(1L, "Rojo", FRIZA);
    private static final PaletaColores ROJO_JERSEY = color(2L, "Rojo", JERSEY);
    private static final PaletaColores ROJO_RIBB = color(3L, "Rojo", RIBB);
    private static final PaletaColores BLANCO_RIBB = color(4L, "Blanco", RIBB);

    private static final Pieza ESPALDA = pieza(10L, "Espalda", null);
    private static final Pieza MANGA = pieza(11L, "Manga", null);
    private static final Pieza CAPUCHA = pieza(12L, "Capucha", TIPO_CAPUCHA);
    private static final Pieza PUNO = pieza(13L, "Puño", TIPO_PUNOS_CINTURA);
    private static final Pieza CINTURA = pieza(14L, "Cintura", TIPO_PUNOS_CINTURA);

    private Producto buzo;
    private PatronCortePosicionPieza pinEspalda;
    private PatronCortePosicionPieza pinCapucha;
    private PatronCortePosicionPieza pinPuno1;
    private PatronCortePosicionPieza pinPuno2;
    private PatronCortePosicionPieza pinCintura;

    @BeforeEach
    void setUp() {
        buzo = productoConDisenoCargado(1L, "Buzo", 10, ROJO_FRIZA, 300, 300);
        pinEspalda = pin(100L, buzo, 0, ESPALDA, null);
        pin(101L, buzo, 0, MANGA, null);
        pin(102L, buzo, 1, MANGA, null);
        pinCapucha = pin(103L, buzo, 0, CAPUCHA, null);
        pinPuno1 = pin(104L, buzo, 0, PUNO, null);
        pinPuno2 = pin(105L, buzo, 1, PUNO, null);
        pinCintura = pin(106L, buzo, 1, CINTURA, "Cintura baja");
    }

    @Test
    void pinesPorTipo_agrupaSoloLasPiezasConTipoDeInsumo() {
        assertThat(AsignacionInsumoPieza.pines(buzo)).hasSize(7);
        assertThat(AsignacionInsumoPieza.pinesPorTipo(buzo))
            .containsOnlyKeys(TIPO_CAPUCHA, TIPO_PUNOS_CINTURA)
            .containsEntry(TIPO_CAPUCHA, List.of(pinCapucha))
            .containsEntry(TIPO_PUNOS_CINTURA, List.of(pinPuno1, pinPuno2, pinCintura));
    }

    @Test
    void conUnaSolaFilaDelTipo_noHaceFaltaAsignarNada() {
        agregar(buzo, capucha(ROJO_JERSEY, 70f), punosYCintura(ROJO_RIBB, 60f));

        assertThat(AsignacionInsumoPieza.requiereAsignacion(buzo, TIPO_PUNOS_CINTURA)).isFalse();
        assertThat(AsignacionInsumoPieza.pinesSinAsignar(buzo)).isEmpty();
    }

    @Test
    void sinNingunaFilaDelTipo_tampocoHayPinesSinAsignar() {
        assertThat(AsignacionInsumoPieza.requiereAsignacion(buzo, TIPO_PUNOS_CINTURA)).isFalse();
        assertThat(AsignacionInsumoPieza.pinesSinAsignar(buzo)).isEmpty();
    }

    @Test
    void conDosFilasDelTipo_cadaPinDeEseTipoDebeEstarAsignado() {
        agregar(buzo, capucha(ROJO_JERSEY, 70f), punosYCintura(ROJO_RIBB, 30f), punosYCintura(BLANCO_RIBB, 30f));

        assertThat(AsignacionInsumoPieza.requiereAsignacion(buzo, TIPO_PUNOS_CINTURA)).isTrue();
        assertThat(AsignacionInsumoPieza.requiereAsignacion(buzo, TIPO_CAPUCHA)).isFalse();
        assertThat(AsignacionInsumoPieza.pinesSinAsignar(buzo)).containsExactly(pinPuno1, pinPuno2, pinCintura);
    }

    @Test
    void asignacionParcial_informaSoloLosPinesQueFaltan() {
        ProductoInsumoSecundario rojos = punosYCintura(ROJO_RIBB, 30f);
        ProductoInsumoSecundario blancos = punosYCintura(BLANCO_RIBB, 30f);
        agregar(buzo, rojos, blancos);
        asignar(rojos, pinPuno1);
        asignar(blancos, pinCintura);

        assertThat(AsignacionInsumoPieza.filaAsignada(buzo, pinPuno1)).containsSame(rojos);
        assertThat(AsignacionInsumoPieza.filaAsignada(buzo, pinCintura)).containsSame(blancos);
        assertThat(AsignacionInsumoPieza.filaAsignada(buzo, pinPuno2)).isEmpty();
        assertThat(AsignacionInsumoPieza.pinesSinAsignar(buzo)).containsExactly(pinPuno2);
    }

    @Test
    void todosLosPinesAsignados_noQuedaNingunoPendiente() {
        ProductoInsumoSecundario rojos = punosYCintura(ROJO_RIBB, 30f);
        ProductoInsumoSecundario blancos = punosYCintura(BLANCO_RIBB, 30f);
        agregar(buzo, rojos, blancos);
        asignar(rojos, pinPuno1);
        asignar(rojos, pinPuno2);
        asignar(blancos, pinCintura);

        assertThat(AsignacionInsumoPieza.pinesSinAsignar(buzo)).isEmpty();
    }

    /** Una asignación a una fila de otro tipo que la pieza del pin no cuenta. */
    @Test
    void asignacionAUnaFilaDeOtroTipo_noCuenta() {
        ProductoInsumoSecundario capucha = capucha(ROJO_JERSEY, 70f);
        agregar(buzo, capucha, punosYCintura(ROJO_RIBB, 30f), punosYCintura(BLANCO_RIBB, 30f));
        asignar(capucha, pinPuno1);

        assertThat(AsignacionInsumoPieza.filaAsignada(buzo, pinPuno1)).isEmpty();
        assertThat(AsignacionInsumoPieza.pinesSinAsignar(buzo)).contains(pinPuno1);
    }

    @Test
    void unPinDelCuerpo_nuncaTieneFilaAsignada() {
        agregar(buzo, punosYCintura(ROJO_RIBB, 30f), punosYCintura(BLANCO_RIBB, 30f));

        assertThat(AsignacionInsumoPieza.filaAsignada(buzo, pinEspalda)).isEmpty();
        assertThat(AsignacionInsumoPieza.pinesSinAsignar(buzo)).doesNotContain(pinEspalda);
    }

    @Test
    void productoSinMolderia_noTienePines() {
        Producto sinMolderia = producto(2L, "Buzo", 10);
        agregar(sinMolderia, punosYCintura(ROJO_RIBB, 30f), punosYCintura(BLANCO_RIBB, 30f));

        assertThat(AsignacionInsumoPieza.pines(sinMolderia)).isEmpty();
        assertThat(AsignacionInsumoPieza.pinesSinAsignar(sinMolderia)).isEmpty();
    }

    @Test
    void etiqueta_usaLaDelPinOElNombreDeLaPiezaNumeradoSiSeRepite() {
        assertThat(AsignacionInsumoPieza.etiqueta(buzo, pinCintura)).isEqualTo("Cintura baja");
        assertThat(AsignacionInsumoPieza.etiqueta(buzo, pinPuno1)).isEqualTo("Puño 1");
        assertThat(AsignacionInsumoPieza.etiqueta(buzo, pinPuno2)).isEqualTo("Puño 2");
        assertThat(AsignacionInsumoPieza.etiqueta(buzo, pinCapucha)).isEqualTo("Capucha");
    }
}
