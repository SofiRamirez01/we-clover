package com.weclover.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.EstadoProduccion;
import com.weclover.backend.entity.EtapaProduccion;
import com.weclover.backend.entity.HistorialEstadoPedido;
import com.weclover.backend.entity.MovimientoEstado;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoEtapaProduccion;
import com.weclover.backend.entity.TipoPrenda;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.repository.MovimientoEstadoRepository;
import com.weclover.backend.repository.ProductoEtapaProduccionRepository;
import com.weclover.backend.repository.ProductoRepository;
import com.weclover.backend.repository.UsuarioRepository;

/** Estado de producción persistido + registro de MovimientoEstado (Módulo 5, paso 1). */
@ExtendWith(MockitoExtension.class)
class ProductoEtapaProduccionServiceTest {

    private static final Long ID_ACTOR = 7L;

    @Mock private ProductoEtapaProduccionRepository productoEtapaProduccionRepository;
    @Mock private MovimientoEstadoRepository movimientoEstadoRepository;
    @Mock private ProductoRepository productoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AutorizacionService autorizacionService;
    @Mock private EstadoPedidoService estadoPedidoService;

    @InjectMocks
    private ProductoEtapaProduccionService service;

    private Usuario actor;
    private Pedido pedido;

    @BeforeEach
    void setUp() {
        actor = Usuario.builder().id(ID_ACTOR).nombre("Planta").build();
        pedido = Pedido.builder().id(1L).estadoActual(EstadoPedido.EN_PRODUCCION).build();
        lenient().when(usuarioRepository.findById(ID_ACTOR)).thenReturn(Optional.of(actor));
    }

    /** Producto con todas sus etapas aplicables ya creadas (sin completar) y estado PENDIENTE. */
    private Producto producto(String tipoPrenda, EtapaProduccion... etapas) {
        Producto producto = Producto.builder()
            .id(10L)
            .pedido(pedido)
            .tipoPrenda(TipoPrenda.builder().nombre(tipoPrenda).build())
            .cantidadTotal(20)
            .estadoProduccion(EstadoProduccion.PENDIENTE)
            .etapas(new ArrayList<>())
            .insumosSecundarios(new ArrayList<>())
            .build();
        for (EtapaProduccion etapa : etapas) {
            producto.getEtapas().add(ProductoEtapaProduccion.builder().producto(producto).etapa(etapa).completado(false).build());
        }
        lenient().when(productoRepository.findById(producto.getId())).thenReturn(Optional.of(producto));
        lenient().when(productoEtapaProduccionRepository.findByProductoAndEtapa(eq(producto), any()))
            .thenAnswer(inv -> producto.getEtapas().stream()
                .filter(f -> f.getEtapa() == inv.getArgument(1)).findFirst());
        lenient().when(productoEtapaProduccionRepository.findByProducto(producto)).thenReturn(producto.getEtapas());
        return producto;
    }

    private Producto remera() {
        return producto("Remera", EtapaProduccion.CORTE, EtapaProduccion.BORDADO, EtapaProduccion.CONFECCION,
            EtapaProduccion.APODO, EtapaProduccion.CONTROL);
    }

    private MovimientoEstado ultimoMovimientoGuardado(int vecesEsperadas) {
        ArgumentCaptor<MovimientoEstado> captor = ArgumentCaptor.forClass(MovimientoEstado.class);
        verify(movimientoEstadoRepository, times(vecesEsperadas)).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void marcarEtapa_generaMovimientoConEstadoAnteriorYNuevo() {
        Producto producto = remera();

        service.marcarEtapa(producto.getId(), EtapaProduccion.CORTE, true, null, null, ID_ACTOR);

        MovimientoEstado movimiento = ultimoMovimientoGuardado(1);
        assertThat(movimiento.getProducto()).isSameAs(producto);
        assertThat(movimiento.getEtapa()).isEqualTo(EtapaProduccion.CORTE);
        assertThat(movimiento.getCompletado()).isTrue();
        assertThat(movimiento.getEstadoAnterior()).isEqualTo(EstadoProduccion.PENDIENTE);
        assertThat(movimiento.getEstadoNuevo()).isEqualTo(EstadoProduccion.CORTADO);
        assertThat(movimiento.getUsuario()).isSameAs(actor);
        assertThat(movimiento.getUnidades()).isEqualTo(20);
        assertThat(movimiento.getFechaHora()).isNotNull();
        assertThat(producto.getEstadoProduccion()).isEqualTo(EstadoProduccion.CORTADO);
    }

    @Test
    void marcarEtapa_guardaElComentarioOpcionalEnElMovimiento() {
        Producto producto = remera();

        service.marcarEtapa(producto.getId(), EtapaProduccion.BORDADO, true, null, "  se repite bordado por falla ", ID_ACTOR);
        assertThat(ultimoMovimientoGuardado(1).getObservaciones()).isEqualTo("se repite bordado por falla");

        service.marcarEtapa(producto.getId(), EtapaProduccion.BORDADO, false, null, "  ", ID_ACTOR);
        assertThat(ultimoMovimientoGuardado(2).getObservaciones()).isNull();
    }

    @Test
    void marcarEtapa_sinCambioDeEstado_igualQuedaRegistrado() {
        Producto producto = remera();
        producto.getEtapas().get(2).setCompletado(true); // CONFECCION
        producto.setEstadoProduccion(EstadoProduccion.CONFECCION);

        // CORTE es anterior a CONFECCION: el estado sigue siendo la etapa de mayor orden.
        service.marcarEtapa(producto.getId(), EtapaProduccion.CORTE, true, null, null, ID_ACTOR);

        MovimientoEstado movimiento = ultimoMovimientoGuardado(1);
        assertThat(movimiento.getEstadoAnterior()).isEqualTo(EstadoProduccion.CONFECCION);
        assertThat(movimiento.getEstadoNuevo()).isEqualTo(EstadoProduccion.CONFECCION);
    }

    @Test
    void marcarUltimaEtapaAplicable_pasaATerminado() {
        Producto producto = remera();
        producto.getEtapas().stream()
            .filter(f -> f.getEtapa() != EtapaProduccion.CONTROL)
            .forEach(f -> f.setCompletado(true));
        producto.setEstadoProduccion(EstadoProduccion.APODO);

        service.marcarEtapa(producto.getId(), EtapaProduccion.CONTROL, true, null, null, ID_ACTOR);

        MovimientoEstado movimiento = ultimoMovimientoGuardado(1);
        assertThat(movimiento.getEstadoAnterior()).isEqualTo(EstadoProduccion.APODO);
        assertThat(movimiento.getEstadoNuevo()).isEqualTo(EstadoProduccion.TERMINADO);
        assertThat(producto.getEstadoProduccion()).isEqualTo(EstadoProduccion.TERMINADO);
    }

    @Test
    void recalcular_pedidoEntregado_registraMovimientoEntregadoConActor() {
        Producto producto = remera();
        producto.setEstadoProduccion(EstadoProduccion.TERMINADO);
        pedido.setEstadoActual(EstadoPedido.ENTREGADO);

        service.recalcularEstadoProduccion(producto, actor, "Pedido marcado como ENTREGADO");

        MovimientoEstado movimiento = ultimoMovimientoGuardado(1);
        assertThat(movimiento.getEtapa()).isNull();
        assertThat(movimiento.getEstadoAnterior()).isEqualTo(EstadoProduccion.TERMINADO);
        assertThat(movimiento.getEstadoNuevo()).isEqualTo(EstadoProduccion.ENTREGADO);
        assertThat(movimiento.getUsuario()).isSameAs(actor);
        assertThat(producto.getEstadoProduccion()).isEqualTo(EstadoProduccion.ENTREGADO);
    }

    @Test
    void recalcular_sinCambio_noRegistraMovimiento() {
        Producto producto = remera();

        service.recalcularEstadoProduccion(producto, actor, "x");

        verify(movimientoEstadoRepository, never()).save(any());
        assertThat(producto.getEstadoProduccion()).isEqualTo(EstadoProduccion.PENDIENTE);
    }

    @Test
    void sincronizar_productoNuevo_asignaEstadoInicialSinMovimiento() {
        Producto producto = remera();
        producto.setEstadoProduccion(null);

        service.sincronizarEtapas(producto);

        assertThat(producto.getEstadoProduccion()).isEqualTo(EstadoProduccion.PENDIENTE);
        verify(movimientoEstadoRepository, never()).save(any());
    }

    @Test
    void calculador_ojalSoloCuentaParaChomba() {
        Producto chomba = producto("Chomba", EtapaProduccion.values());
        chomba.getEtapas().stream()
            .filter(f -> f.getEtapa() == EtapaProduccion.OJAL || f.getEtapa() == EtapaProduccion.CORTE)
            .forEach(f -> f.setCompletado(true));
        assertThat(EstadoProduccionCalculador.calcular(chomba)).isEqualTo(EstadoProduccion.OJAL);

        // Una Remera con una fila OJAL vieja (ej. antes era Chomba): esa fila no aplica.
        chomba.getTipoPrenda().setNombre("Remera");
        assertThat(EstadoProduccionCalculador.calcular(chomba)).isEqualTo(EstadoProduccion.CORTADO);
    }

    @Test
    void calculador_banderaNoTieneEstado() {
        Producto bandera = Producto.builder()
            .pedido(pedido)
            .tipoPrenda(TipoPrenda.builder().nombre("Bandera").build())
            .build();
        assertThat(EstadoProduccionCalculador.calcular(bandera)).isNull();
    }

    @Test
    void inicializarEstadosFaltantes_terminadoGeneraMovimientoRetroactivoFechado() {
        Producto producto = remera();
        producto.setEstadoProduccion(null);
        producto.getEtapas().forEach(f -> {
            f.setCompletado(true);
            f.setFechaCompletado(LocalDate.of(2026, 9, 1));
        });
        producto.getEtapas().get(4).setFechaCompletado(LocalDate.of(2026, 9, 12));
        when(productoRepository.findByEstadoProduccionIsNullAndHabilitadoTrue()).thenReturn(List.of(producto));

        int inicializados = service.inicializarEstadosFaltantes();

        assertThat(inicializados).isEqualTo(1);
        assertThat(producto.getEstadoProduccion()).isEqualTo(EstadoProduccion.TERMINADO);
        MovimientoEstado movimiento = ultimoMovimientoGuardado(1);
        assertThat(movimiento.getEstadoAnterior()).isNull();
        assertThat(movimiento.getEstadoNuevo()).isEqualTo(EstadoProduccion.TERMINADO);
        assertThat(movimiento.getFechaHora()).isEqualTo(LocalDateTime.of(2026, 9, 12, 0, 0));
        assertThat(movimiento.getUsuario()).isNull();
        assertThat(movimiento.getObservaciones()).isEqualTo(ProductoEtapaProduccionService.OBSERVACION_RETROACTIVA);
    }

    @Test
    void inicializarEstadosFaltantes_entregadoGeneraTerminadoYEntregado() {
        Producto producto = remera();
        producto.setEstadoProduccion(null);
        producto.getEtapas().forEach(f -> {
            f.setCompletado(true);
            f.setFechaCompletado(LocalDate.of(2026, 9, 1));
        });
        pedido.setEstadoActual(EstadoPedido.ENTREGADO);
        pedido.getHistorial().add(HistorialEstadoPedido.builder()
            .estado(EstadoPedido.ENTREGADO).fechaCambio(LocalDateTime.of(2026, 9, 20, 10, 0)).build());
        when(productoRepository.findByEstadoProduccionIsNullAndHabilitadoTrue()).thenReturn(List.of(producto));

        service.inicializarEstadosFaltantes();

        ArgumentCaptor<MovimientoEstado> captor = ArgumentCaptor.forClass(MovimientoEstado.class);
        verify(movimientoEstadoRepository, times(2)).save(captor.capture());
        MovimientoEstado entregado = captor.getAllValues().get(1);
        assertThat(entregado.getEstadoAnterior()).isEqualTo(EstadoProduccion.TERMINADO);
        assertThat(entregado.getEstadoNuevo()).isEqualTo(EstadoProduccion.ENTREGADO);
        assertThat(entregado.getFechaHora()).isEqualTo(LocalDateTime.of(2026, 9, 20, 10, 0));
        assertThat(producto.getEstadoProduccion()).isEqualTo(EstadoProduccion.ENTREGADO);
    }

    @Test
    void darDeBaja_deshabilitaYRegistraMovimientoConFecha() {
        Producto producto = remera();
        producto.setEstadoProduccion(EstadoProduccion.BORDADO);

        service.darDeBaja(producto, actor);

        assertThat(producto.isHabilitado()).isFalse();
        MovimientoEstado movimiento = ultimoMovimientoGuardado(1);
        assertThat(movimiento.getEstadoAnterior()).isEqualTo(EstadoProduccion.BORDADO);
        assertThat(movimiento.getEstadoNuevo()).isNull();
        assertThat(movimiento.getFechaHora()).isNotNull();
        assertThat(movimiento.getUsuario()).isSameAs(actor);
        assertThat(movimiento.getObservaciones()).isEqualTo(ProductoEtapaProduccionService.OBSERVACION_BAJA);
        verify(productoRepository).save(producto);
    }
}
