package com.weclover.backend.service;

import static com.weclover.backend.service.InsumosDePrueba.FRIZA;
import static com.weclover.backend.service.InsumosDePrueba.JERSEY;
import static com.weclover.backend.service.InsumosDePrueba.RIBB;
import static com.weclover.backend.service.InsumosDePrueba.agregar;
import static com.weclover.backend.service.InsumosDePrueba.capucha;
import static com.weclover.backend.service.InsumosDePrueba.color;
import static com.weclover.backend.service.InsumosDePrueba.estampado;
import static com.weclover.backend.service.InsumosDePrueba.libre;
import static com.weclover.backend.service.InsumosDePrueba.producto;
import static com.weclover.backend.service.InsumosDePrueba.punosYCintura;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.weclover.backend.entity.EtapaProduccion;
import com.weclover.backend.entity.Producto;

/** Regresión: qué etapas de producción aplican a un producto según sus insumos y su prenda. */
class EtapaProduccionAplicabilidadTest {

    @Test
    void sinEstampado_noAplicaLaEtapaEstampado() {
        Producto buzo = producto(1L, "Buzo", 10);
        agregar(buzo, capucha(color(1L, "Rojo", JERSEY), 70f), punosYCintura(color(2L, "Rojo", RIBB), 60f));

        assertThat(EtapaProduccionAplicabilidad.etapasAplicables(buzo))
            .containsExactly(EtapaProduccion.CORTE, EtapaProduccion.BORDADO, EtapaProduccion.CONFECCION,
                EtapaProduccion.APODO, EtapaProduccion.CONTROL)
            .doesNotContain(EtapaProduccion.ESTAMPADO, EtapaProduccion.OJAL);
    }

    @Test
    void conEstampado_aplicaLaEtapaEstampado() {
        Producto remera = producto(1L, "Remera", 10);
        agregar(remera, estampado());

        assertThat(EtapaProduccionAplicabilidad.etapasAplicables(remera))
            .contains(EtapaProduccion.ESTAMPADO)
            .doesNotContain(EtapaProduccion.OJAL);
    }

    @Test
    void unInsumoLibreCualquiera_noHaceAplicarEstampado() {
        Producto remera = producto(1L, "Remera", 10);
        agregar(remera, libre("Bolsillo", FRIZA, color(3L, "Rojo", FRIZA), 20f));

        assertThat(EtapaProduccionAplicabilidad.etapasAplicables(remera)).doesNotContain(EtapaProduccion.ESTAMPADO);
    }

    @Test
    void chomba_aplicaOjal_yEstampadoSoloSiLoLleva() {
        Producto chomba = producto(1L, "Chomba", 10);

        assertThat(EtapaProduccionAplicabilidad.etapasAplicables(chomba))
            .contains(EtapaProduccion.OJAL)
            .doesNotContain(EtapaProduccion.ESTAMPADO);

        agregar(chomba, estampado());

        assertThat(EtapaProduccionAplicabilidad.etapasAplicables(chomba))
            .contains(EtapaProduccion.OJAL, EtapaProduccion.ESTAMPADO);
    }
}
