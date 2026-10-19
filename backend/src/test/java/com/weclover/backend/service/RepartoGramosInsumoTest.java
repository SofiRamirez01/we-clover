package com.weclover.backend.service;

import static com.weclover.backend.service.InsumosDePrueba.CIERRE;
import static com.weclover.backend.service.InsumosDePrueba.FRIZA;
import static com.weclover.backend.service.InsumosDePrueba.JERSEY;
import static com.weclover.backend.service.InsumosDePrueba.RIBB;
import static com.weclover.backend.service.InsumosDePrueba.agregar;
import static com.weclover.backend.service.InsumosDePrueba.capucha;
import static com.weclover.backend.service.InsumosDePrueba.cierre;
import static com.weclover.backend.service.InsumosDePrueba.color;
import static com.weclover.backend.service.InsumosDePrueba.estampado;
import static com.weclover.backend.service.InsumosDePrueba.libre;
import static com.weclover.backend.service.InsumosDePrueba.producto;
import static com.weclover.backend.service.InsumosDePrueba.punosYCintura;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoInsumoSecundario;

/** Reparto de los gramos sugeridos entre las filas de un mismo tipo de insumo. */
class RepartoGramosInsumoTest {

    private static final PaletaColores ROJO_JERSEY = color(2L, "Rojo", JERSEY);
    private static final PaletaColores ROJO_RIBB = color(3L, "Rojo", RIBB);
    private static final PaletaColores BLANCO_RIBB = color(4L, "Blanco", RIBB);

    private static List<Float> gramos(Producto producto) {
        return producto.getInsumosSecundarios().stream().map(ProductoInsumoSecundario::getCantidad).toList();
    }

    @Test
    void repartir_enterosQueSumanExacto() {
        assertThat(RepartoGramosInsumo.repartir(60, 1)).containsExactly(60);
        assertThat(RepartoGramosInsumo.repartir(60, 2)).containsExactly(30, 30);
        assertThat(RepartoGramosInsumo.repartir(60, 3)).containsExactly(20, 20, 20);
        assertThat(RepartoGramosInsumo.repartir(70, 3)).containsExactly(24, 23, 23);
        assertThat(RepartoGramosInsumo.repartir(70, 4)).containsExactly(18, 18, 17, 17);
        assertThat(RepartoGramosInsumo.repartir(2, 3)).containsExactly(1, 1, 0);
    }

    @Test
    void aplicar_reparteCadaTipoPorSeparado() {
        Producto buzo = producto(1L, "Buzo", 10);
        agregar(buzo, capucha(ROJO_JERSEY, 1f), punosYCintura(ROJO_RIBB, 1f), punosYCintura(BLANCO_RIBB, 1f));

        RepartoGramosInsumo.aplicar(buzo);

        assertThat(gramos(buzo)).containsExactly(70f, 30f, 30f);
    }

    @Test
    void aplicar_unaFilaEditadaAManoCongelaSoloSuTipo() {
        Producto buzo = producto(1L, "Buzo", 10);
        ProductoInsumoSecundario editada = punosYCintura(ROJO_RIBB, 50f);
        editada.setCantidadEditadaManualmente(true);
        agregar(buzo, capucha(ROJO_JERSEY, 1f), editada, punosYCintura(BLANCO_RIBB, 10f));

        RepartoGramosInsumo.aplicar(buzo);

        assertThat(gramos(buzo)).containsExactly(70f, 50f, 10f);
    }

    /** La fila de otra tela congela igual al tipo si fue editada a mano: el grupo es el tipo. */
    @Test
    void aplicar_laEdicionManualEnOtraTelaTambienCongelaAlTipo() {
        Producto buzo = producto(1L, "Buzo", 10);
        ProductoInsumoSecundario enJersey = punosYCintura(ROJO_JERSEY, 40f);
        enJersey.setTipoTela(JERSEY);
        enJersey.setCantidadEditadaManualmente(true);
        agregar(buzo, punosYCintura(ROJO_RIBB, 1f), punosYCintura(BLANCO_RIBB, 1f), enJersey);

        RepartoGramosInsumo.aplicar(buzo);

        assertThat(gramos(buzo)).containsExactly(1f, 1f, 40f);
    }

    @Test
    void aplicar_noTocaCierreEstampadoLibresNiTelasSinSugerido() {
        Producto campera = producto(1L, "Campera", 10);
        ProductoInsumoSecundario capuchaEnFriza = capucha(color(1L, "Rojo", FRIZA), 80f);
        capuchaEnFriza.setTipoTela(FRIZA);
        agregar(campera, cierre(color(5L, "Rojo", CIERRE)), estampado(),
            libre("Bolsillo", RIBB, ROJO_RIBB, 20f), capuchaEnFriza);

        RepartoGramosInsumo.aplicar(campera);

        assertThat(gramos(campera)).containsExactly(1f, null, 20f, 80f);
        assertThat(RepartoGramosInsumo.gramosEsperados(campera)).isEmpty();
    }

    /** null (fila anterior a la columna, todavía sin evaluar) cuenta como no editada. */
    @Test
    void indicadorEnNull_cuentaComoNoEditada() {
        Producto buzo = producto(1L, "Buzo", 10);
        ProductoInsumoSecundario vieja = punosYCintura(ROJO_RIBB, 45f);
        vieja.setCantidadEditadaManualmente(null);
        agregar(buzo, vieja);

        assertThat(RepartoGramosInsumo.editadaAMano(vieja)).isFalse();
        RepartoGramosInsumo.aplicar(buzo);
        assertThat(vieja.getCantidad()).isEqualTo(60f);
    }
}
