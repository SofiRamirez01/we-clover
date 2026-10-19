package com.weclover.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoInsumoSecundario;
import com.weclover.backend.entity.TipoInsumo;
import com.weclover.backend.repository.ProductoInsumoSecundarioRepository;
import com.weclover.backend.repository.TipoInsumoRepository;

/** Migración de arranque: asociación de filas de insumo a su tipo por descripción exacta. */
@ExtendWith(MockitoExtension.class)
class TipoInsumoMigracionServiceTest {

    private static final TipoInsumo CAPUCHA = tipo(1L, "CAPUCHA", "Capucha");
    private static final TipoInsumo PUNOS_CINTURA = tipo(2L, "PUNOS_CINTURA", "Puños y cintura");
    private static final TipoInsumo CIERRE = tipo(3L, "CIERRE", "Cierre");
    private static final TipoInsumo ESTAMPADO = tipo(4L, "ESTAMPADO", "Estampado");

    @Mock private ProductoInsumoSecundarioRepository productoInsumoSecundarioRepository;
    @Mock private TipoInsumoRepository tipoInsumoRepository;

    @InjectMocks
    private TipoInsumoMigracionService service;

    @BeforeEach
    void setUp() {
        lenient().when(tipoInsumoRepository.findAll()).thenReturn(List.of(CAPUCHA, PUNOS_CINTURA, CIERRE, ESTAMPADO));
    }

    private static TipoInsumo tipo(Long id, String codigo, String nombre) {
        return TipoInsumo.builder().id(id).codigo(codigo).nombre(nombre).build();
    }

    private static ProductoInsumoSecundario fila(String descripcion) {
        return ProductoInsumoSecundario.builder().descripcion(descripcion).cantidad(60f).build();
    }

    private TipoInsumoMigracionService.Reporte migrar(ProductoInsumoSecundario... filasSinTipo) {
        when(productoInsumoSecundarioRepository.findByTipoInsumoIsNull()).thenReturn(List.of(filasSinTipo));
        return service.asociarInsumosPorDescripcion();
    }

    @Test
    void descripcionIgualAlNombre_seAsociaAEseTipo() {
        ProductoInsumoSecundario capucha = fila("Capucha");
        ProductoInsumoSecundario punos = fila("Puños y cintura");
        ProductoInsumoSecundario cierre = fila("Cierre");

        migrar(capucha, punos, cierre);

        assertThat(capucha.getTipoInsumo()).isSameAs(CAPUCHA);
        assertThat(punos.getTipoInsumo()).isSameAs(PUNOS_CINTURA);
        assertThat(cierre.getTipoInsumo()).isSameAs(CIERRE);
        verify(productoInsumoSecundarioRepository, times(3)).save(any());
    }

    @Test
    void noDistingueMayusculasNiEspacios() {
        ProductoInsumoSecundario mayusculas = fila("CAPUCHA");
        ProductoInsumoSecundario conEspacios = fila("  puños  Y cintura ");

        migrar(mayusculas, conEspacios);

        assertThat(mayusculas.getTipoInsumo()).isSameAs(CAPUCHA);
        assertThat(conEspacios.getTipoInsumo()).isSameAs(PUNOS_CINTURA);
    }

    @Test
    void descripcionParecidaPeroNoIgual_quedaLibre() {
        ProductoInsumoSecundario bolsillo = fila("Bolsillo");
        ProductoInsumoSecundario soloPunos = fila("Puños");
        ProductoInsumoSecundario sinEnie = fila("Punos y cintura");
        ProductoInsumoSecundario conAgregado = fila("Capucha forrada");

        migrar(bolsillo, soloPunos, sinEnie, conAgregado);

        assertThat(List.of(bolsillo, soloPunos, sinEnie, conAgregado))
            .allSatisfy(fila -> assertThat(fila.getTipoInsumo()).isNull());
        verify(productoInsumoSecundarioRepository, never()).save(any());
    }

    @Test
    void soloCambiaElTipo_noLaDescripcionNiLaCantidad() {
        ProductoInsumoSecundario capucha = fila(" capucha");

        migrar(capucha);

        assertThat(capucha.getDescripcion()).isEqualTo(" capucha");
        assertThat(capucha.getCantidad()).isEqualTo(60f);
    }

    @Test
    void reporte_cuentaAsociadasPorTipoYLibresPorDescripcion() {
        TipoInsumoMigracionService.Reporte reporte = migrar(
            fila("Capucha"), fila("capucha"), fila("Cierre"), fila("Bolsillo"), fila("Bolsillo"), fila("Vivo"));

        assertThat(reporte.asociadasPorTipo()).containsOnly(entry("CAPUCHA", 2), entry("CIERRE", 1));
        assertThat(reporte.libresPorDescripcion()).containsOnly(entry("Bolsillo", 2), entry("Vivo", 1));
        assertThat(reporte.totalAsociadas()).isEqualTo(3);
        assertThat(reporte.totalLibres()).isEqualTo(3);
    }

    // ---------- Indicador de edición manual ----------

    /** Producto con esas filas (todas con el indicador en null, como recién migrada la columna). */
    private Producto productoSinEvaluar(Long id, ProductoInsumoSecundario... filas) {
        Producto producto = InsumosDePrueba.producto(id, "Buzo", 10);
        InsumosDePrueba.agregar(producto, filas);
        producto.getInsumosSecundarios().forEach(fila -> fila.setCantidadEditadaManualmente(null));
        return producto;
    }

    private TipoInsumoMigracionService.ReporteGramos evaluar(Producto... productos) {
        List<ProductoInsumoSecundario> sinEvaluar = Arrays.stream(productos)
            .flatMap(producto -> producto.getInsumosSecundarios().stream())
            .filter(fila -> fila.getCantidadEditadaManualmente() == null)
            .toList();
        when(productoInsumoSecundarioRepository.findByCantidadEditadaManualmenteIsNull()).thenReturn(sinEvaluar);
        return service.marcarCantidadesEditadasAMano();
    }

    private static final PaletaColores ROJO_RIBB =
        InsumosDePrueba.color(3L, "Rojo", InsumosDePrueba.RIBB);
    private static final PaletaColores BLANCO_RIBB =
        InsumosDePrueba.color(4L, "Blanco", InsumosDePrueba.RIBB);
    private static final PaletaColores ROJO_JERSEY =
        InsumosDePrueba.color(2L, "Rojo", InsumosDePrueba.JERSEY);

    @Test
    void filaUnicaConElSugerido_quedaComoSugerida_yLaQueDifiereComoEditada() {
        Producto conSugerido = productoSinEvaluar(1L, InsumosDePrueba.capucha(ROJO_JERSEY, 70f), InsumosDePrueba.punosYCintura(ROJO_RIBB, 60f));
        Producto conOtroValor = productoSinEvaluar(2L, InsumosDePrueba.capucha(ROJO_JERSEY, 65f));

        TipoInsumoMigracionService.ReporteGramos reporte = evaluar(conSugerido, conOtroValor);

        assertThat(conSugerido.getInsumosSecundarios()).allSatisfy(fila -> assertThat(fila.getCantidadEditadaManualmente()).isFalse());
        assertThat(conOtroValor.getInsumosSecundarios().get(0).getCantidadEditadaManualmente()).isTrue();
        assertThat(reporte.marcadasSugeridas()).isEqualTo(2);
        assertThat(reporte.marcadasEditadas()).isEqualTo(1);
        assertThat(reporte.gruposConSumaDistinta()).isEmpty();
    }

    @Test
    void dosFilasQueNoSonElRepartoExacto_quedanEditadas_aunqueSumenElSugerido() {
        Producto producto = productoSinEvaluar(20L, InsumosDePrueba.punosYCintura(ROJO_RIBB, 50f), InsumosDePrueba.punosYCintura(BLANCO_RIBB, 10f));

        TipoInsumoMigracionService.ReporteGramos reporte = evaluar(producto);

        assertThat(producto.getInsumosSecundarios()).allSatisfy(fila -> assertThat(fila.getCantidadEditadaManualmente()).isTrue());
        assertThat(producto.getInsumosSecundarios()).extracting(ProductoInsumoSecundario::getCantidad).containsExactly(50f, 10f);
        assertThat(reporte.gruposConSumaDistinta()).isEmpty();
    }

    @Test
    void dosFilasConElRepartoExacto_quedanSugeridas() {
        Producto producto = productoSinEvaluar(1L, InsumosDePrueba.punosYCintura(ROJO_RIBB, 30f), InsumosDePrueba.punosYCintura(BLANCO_RIBB, 30f));

        evaluar(producto);

        assertThat(producto.getInsumosSecundarios()).allSatisfy(fila -> assertThat(fila.getCantidadEditadaManualmente()).isFalse());
    }

    @Test
    void grupoDeVariasFilasQueNoSumaElSugerido_apareceEnElReporte_sinTocarLosGramos() {
        Producto producto = productoSinEvaluar(30L, InsumosDePrueba.punosYCintura(ROJO_RIBB, 10f), InsumosDePrueba.punosYCintura(BLANCO_RIBB, 49.82f));

        TipoInsumoMigracionService.ReporteGramos reporte = evaluar(producto);

        assertThat(reporte.gruposConSumaDistinta())
            .containsExactly("producto #30 PUNOS_CINTURA/RIBB: 2 filas suman 59.82 g (sugerido 60 g)");
        assertThat(producto.getInsumosSecundarios()).extracting(ProductoInsumoSecundario::getCantidad).containsExactly(10f, 49.82f);
        assertThat(reporte.marcadasEditadas()).isEqualTo(2);
    }

    @Test
    void insumoLibreYCierre_quedanComoSugeridos_noParticipanDelReparto() {
        Producto producto = productoSinEvaluar(1L,
            InsumosDePrueba.libre("Bolsillo", InsumosDePrueba.RIBB, ROJO_RIBB, 20f),
            InsumosDePrueba.cierre(InsumosDePrueba.color(5L, "Rojo", InsumosDePrueba.CIERRE)));

        TipoInsumoMigracionService.ReporteGramos reporte = evaluar(producto);

        assertThat(producto.getInsumosSecundarios()).allSatisfy(fila -> assertThat(fila.getCantidadEditadaManualmente()).isFalse());
        assertThat(reporte.marcadasSugeridas()).isEqualTo(2);
    }

    @Test
    void sinFilasSinEvaluar_noHaceNada() {
        TipoInsumoMigracionService.ReporteGramos reporte = evaluar();

        assertThat(reporte.totalEvaluadas()).isZero();
        verify(productoInsumoSecundarioRepository, never()).save(any());
    }

    /** Segunda corrida: las ya asociadas no vuelven (la consulta solo trae filas sin tipo). */
    @Test
    void sinFilasPendientes_noAsociaNada() {
        TipoInsumoMigracionService.Reporte reporte = migrar();

        assertThat(reporte.totalAsociadas()).isZero();
        assertThat(reporte.totalLibres()).isZero();
        verify(productoInsumoSecundarioRepository, never()).save(any());
    }
}
