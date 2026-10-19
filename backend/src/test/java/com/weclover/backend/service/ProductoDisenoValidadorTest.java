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
import static com.weclover.backend.service.InsumosDePrueba.libre;
import static com.weclover.backend.service.InsumosDePrueba.pieza;
import static com.weclover.backend.service.InsumosDePrueba.pin;
import static com.weclover.backend.service.InsumosDePrueba.producto;
import static com.weclover.backend.service.InsumosDePrueba.productoConDisenoCargado;
import static com.weclover.backend.service.InsumosDePrueba.punosYCintura;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.Producto;

/**
 * Regresión de "diseño completo" (condición de LISTO_PARA_PRODUCCION y de elegibilidad en el
 * Planificador de Compras): qué insumos secundarios se exigen según el tipo de prenda.
 */
class ProductoDisenoValidadorTest {

    private static final PaletaColores ROJO_FRIZA = color(1L, "Rojo", FRIZA);
    private static final PaletaColores ROJO_JERSEY = color(2L, "Rojo", JERSEY);
    private static final PaletaColores ROJO_RIBB = color(3L, "Rojo", RIBB);
    private static final PaletaColores BLANCO_RIBB = color(4L, "Blanco", RIBB);
    private static final PaletaColores ROJO_CIERRE = color(5L, "Rojo", CIERRE);

    private static Producto conDiseno(String tipoPrenda) {
        return productoConDisenoCargado(1L, tipoPrenda, 10, ROJO_FRIZA, 600);
    }

    @Test
    void buzoConCapuchaYPunos_estaCompleto() {
        Producto buzo = conDiseno("Buzo");
        agregar(buzo, capucha(ROJO_JERSEY, 70f), punosYCintura(ROJO_RIBB, 60f));

        assertThat(ProductoDisenoValidador.motivoDisenoIncompleto(buzo)).isEmpty();
    }

    @Test
    void buzoSinInsumos_informaLosDosFaltantesEnOrden() {
        assertThat(ProductoDisenoValidador.motivoDisenoIncompleto(conDiseno("Buzo")))
            .contains("Faltan insumos secundarios: Capucha, Puños y cintura");
    }

    @Test
    void buzoSinCapucha_informaSoloLaCapucha() {
        Producto buzo = conDiseno("Buzo");
        agregar(buzo, punosYCintura(ROJO_RIBB, 60f));

        assertThat(ProductoDisenoValidador.motivoDisenoIncompleto(buzo))
            .contains("Faltan insumos secundarios: Capucha");
    }

    @Test
    void camperaSinInsumos_informaCierreCapuchaYPunos() {
        assertThat(ProductoDisenoValidador.motivoDisenoIncompleto(conDiseno("Campera")))
            .contains("Faltan insumos secundarios: Cierre, Capucha, Puños y cintura");
    }

    @Test
    void camperaSinCierre_informaSoloElCierre() {
        Producto campera = conDiseno("Campera");
        agregar(campera, capucha(ROJO_JERSEY, 70f), punosYCintura(ROJO_RIBB, 60f));

        assertThat(ProductoDisenoValidador.motivoDisenoIncompleto(campera))
            .contains("Faltan insumos secundarios: Cierre");
    }

    @Test
    void camperaConLosTresInsumos_estaCompleta() {
        Producto campera = conDiseno("Campera");
        agregar(campera, cierre(ROJO_CIERRE), capucha(ROJO_JERSEY, 70f), punosYCintura(ROJO_RIBB, 60f));

        assertThat(ProductoDisenoValidador.motivoDisenoIncompleto(campera)).isEmpty();
    }

    @Test
    void remeraYChomba_noExigenInsumosSecundarios() {
        assertThat(ProductoDisenoValidador.motivoDisenoIncompleto(conDiseno("Remera"))).isEmpty();
        assertThat(ProductoDisenoValidador.motivoDisenoIncompleto(conDiseno("Chomba"))).isEmpty();
    }

    @Test
    void unInsumoLibre_noReemplazaAUnoExigido() {
        Producto buzo = conDiseno("Buzo");
        agregar(buzo, punosYCintura(ROJO_RIBB, 60f), libre("Bolsillo", FRIZA, ROJO_FRIZA, 20f));

        assertThat(ProductoDisenoValidador.motivoDisenoIncompleto(buzo))
            .contains("Faltan insumos secundarios: Capucha");
    }

    /** Dos filas del mismo insumo (ej. un color por puño) no vuelven incompleta a la ficha. */
    @Test
    void variasFilasDelMismoInsumo_siguenContandoComoCompleto() {
        Producto buzo = conDiseno("Buzo");
        agregar(buzo, capucha(ROJO_JERSEY, 70f), punosYCintura(ROJO_RIBB, 50f), punosYCintura(BLANCO_RIBB, 10f));

        assertThat(ProductoDisenoValidador.motivoDisenoIncompleto(buzo)).isEmpty();
    }

    /**
     * Pines de insumo sin asignar NO vuelven incompleta a la ficha: es un aviso aparte (ver
     * AsignacionInsumoPieza). Ningún producto debe perder LISTO_PARA_PRODUCCION ni la
     * elegibilidad en compras por esto.
     */
    @Test
    void pinesDeInsumoSinAsignar_noVuelvenIncompletaLaFicha() {
        Producto buzo = conDiseno("Buzo");
        agregar(buzo, capucha(ROJO_JERSEY, 70f), punosYCintura(ROJO_RIBB, 30f), punosYCintura(BLANCO_RIBB, 30f));
        pin(900L, buzo, 0, pieza(50L, "Puño", TIPO_PUNOS_CINTURA), null);
        pin(901L, buzo, 0, pieza(50L, "Puño", TIPO_PUNOS_CINTURA), null);
        pin(902L, buzo, 0, pieza(51L, "Cintura", TIPO_PUNOS_CINTURA), null);

        assertThat(AsignacionInsumoPieza.pinesSinAsignar(buzo)).hasSize(3);
        assertThat(ProductoDisenoValidador.motivoDisenoIncompleto(buzo)).isEmpty();
    }

    @Test
    void sinTela_esLoPrimeroQueSeInforma() {
        assertThat(ProductoDisenoValidador.motivoDisenoIncompleto(producto(1L, "Buzo", 10)))
            .contains("Falta asignar la tela de esta prenda");
    }

    @Test
    void sinMolderia_seInformaAntesQueLaImagen() {
        Producto buzo = producto(1L, "Buzo", 10);
        buzo.setTipoTela(FRIZA);

        assertThat(ProductoDisenoValidador.motivoDisenoIncompleto(buzo))
            .contains("Falta asignar la moldería (patrón de corte)");
    }

    @Test
    void sinImagen_seInformaAntesQueLosColores() {
        Producto buzo = conDiseno("Buzo");
        buzo.setImagenDisenoUrl(null);
        buzo.getColores().clear();

        assertThat(ProductoDisenoValidador.motivoDisenoIncompleto(buzo))
            .contains("Falta cargar la imagen de diseño");
    }

    @Test
    void coloresSinMarcar_seInformanAntesQueLosInsumos() {
        Producto buzo = productoConDisenoCargado(1L, "Buzo", 10, ROJO_FRIZA, 300, 300);
        buzo.getColores().remove(1);

        assertThat(ProductoDisenoValidador.motivoDisenoIncompleto(buzo))
            .contains("Faltan colores por marcar (1 de 2 posiciones)");
    }
}
