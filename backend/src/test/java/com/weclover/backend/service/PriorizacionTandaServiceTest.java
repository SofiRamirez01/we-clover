package com.weclover.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.weclover.backend.dto.tanda.MovimientoTandaRequest;
import com.weclover.backend.dto.tanda.PriorizacionRequest;
import com.weclover.backend.dto.tanda.TandaNuevaRequest;
import com.weclover.backend.dto.tanda.TandaRefRequest;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.HistorialTandaPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.SesionPriorizacion;
import com.weclover.backend.entity.Tanda;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ForbiddenException;
import com.weclover.backend.repository.HistorialTandaPedidoRepository;
import com.weclover.backend.repository.PedidoRepository;
import com.weclover.backend.repository.SesionPriorizacionRepository;
import com.weclover.backend.repository.TandaRepository;
import com.weclover.backend.repository.UsuarioRepository;
import com.weclover.backend.service.EstadoPedidoService.RequisitosListo;

import tools.jackson.databind.json.JsonMapper;

/** Sesión de priorización atómica, congelamiento, motivo, snapshot y salida por cancelación
 *  (paso 3 de Tandas). */
@ExtendWith(MockitoExtension.class)
class PriorizacionTandaServiceTest {

    private static final Long ID_ACTOR = 7L;

    @Mock private TandaRepository tandaRepository;
    @Mock private PedidoRepository pedidoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private SesionPriorizacionRepository sesionPriorizacionRepository;
    @Mock private HistorialTandaPedidoRepository historialTandaPedidoRepository;
    @Mock private AutorizacionService autorizacionService;
    @Mock private EstadoPedidoService estadoPedidoService;

    private PriorizacionTandaService service;

    private final Usuario actor = Usuario.builder().id(ID_ACTOR).nombre("Admin").build();
    private final List<Tanda> tandas = new ArrayList<>();
    private final List<Pedido> pedidos = new ArrayList<>();
    private long proximoIdTanda = 100;

    @BeforeEach
    void setUp() {
        TandaService tandaService = new TandaService(tandaRepository, pedidoRepository, autorizacionService);
        service = new PriorizacionTandaService(
            tandaService, estadoPedidoService, autorizacionService, tandaRepository, pedidoRepository,
            usuarioRepository, sesionPriorizacionRepository, historialTandaPedidoRepository,
            JsonMapper.builder().build());

        lenient().when(usuarioRepository.findById(ID_ACTOR)).thenReturn(Optional.of(actor));
        lenient().when(tandaRepository.findAllByOrderByOrdenAscIdAsc()).thenAnswer(inv -> tandas.stream()
            .sorted((a, b) -> Integer.compare(a.getOrden(), b.getOrden()))
            .toList());
        lenient().when(pedidoRepository.findByTandaIsNotNull()).thenAnswer(inv -> pedidos.stream()
            .filter(pedido -> pedido.getTanda() != null)
            .toList());
        lenient().when(pedidoRepository.findById(any())).thenAnswer(inv -> pedidos.stream()
            .filter(pedido -> pedido.getId().equals(inv.getArgument(0)))
            .findFirst());
        // Como la base: al guardar, una tanda nueva recibe id y pasa a existir.
        lenient().when(tandaRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<Tanda> guardadas = inv.getArgument(0);
            for (Tanda tanda : guardadas) {
                if (tanda.getId() == null) {
                    tanda.setId(proximoIdTanda++);
                    tandas.add(tanda);
                }
            }
            return guardadas;
        });
        lenient().when(sesionPriorizacionRepository.save(any())).thenAnswer(inv -> {
            SesionPriorizacion sesion = inv.getArgument(0);
            sesion.setId(500L);
            return sesion;
        });
        lenient().when(estadoPedidoService.evaluarRequisitosListo(any()))
            .thenReturn(new RequisitosListo(true, false, true));
        lenient().when(estadoPedidoService.calcularPorcentajePagado(any())).thenReturn(62.5f);
        lenient().when(estadoPedidoService.calcularPrioridadesAutomaticas()).thenReturn(Map.of(1L, 3, 2L, 8));
    }

    private Tanda tanda(long id, String nombre, int orden) {
        Tanda tanda = Tanda.builder().id(id).nombre(nombre).orden(orden).build();
        tandas.add(tanda);
        return tanda;
    }

    private Pedido pedido(long id, EstadoPedido estado, boolean etapaCompletada, Tanda tanda) {
        Pedido pedido = TandaEstadoCalculadorTest.pedido(id, estado, etapaCompletada);
        pedido.setCodigoInterno("P" + id);
        pedido.setTanda(tanda);
        pedidos.add(pedido);
        return pedido;
    }

    private static TandaRefRequest existente(Tanda tanda) {
        return new TandaRefRequest(tanda.getId(), null);
    }

    private static TandaRefRequest nueva(String idTemporal) {
        return new TandaRefRequest(null, idTemporal);
    }

    private static MovimientoTandaRequest mover(Pedido pedido, Tanda origen, TandaRefRequest destino, String motivo) {
        return new MovimientoTandaRequest(pedido.getId(), origen != null ? origen.getId() : null, destino, motivo);
    }

    private static PriorizacionRequest sesion(MovimientoTandaRequest... movimientos) {
        return new PriorizacionRequest(null, null, null, List.of(movimientos));
    }

    private void verificarQueNoSeGuardoNada() {
        verify(tandaRepository, never()).saveAll(anyList());
        verify(sesionPriorizacionRepository, never()).save(any());
        verify(historialTandaPedidoRepository, never()).save(any());
        verify(pedidoRepository, never()).save(any());
    }

    private HistorialTandaPedido eventoGuardado() {
        ArgumentCaptor<HistorialTandaPedido> captor = ArgumentCaptor.forClass(HistorialTandaPedido.class);
        verify(historialTandaPedidoRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------- Asignación y snapshot ----------

    @Test
    void asignarGuardaElEventoConSesionYSnapshotDelMomento() {
        Tanda a = tanda(1, "A", 1);
        Pedido pedido = pedido(1, EstadoPedido.SENADO, false, null);

        service.aplicar(new PriorizacionRequest("Semana 41", null, null,
            List.of(mover(pedido, null, existente(a), null))), ID_ACTOR);

        assertThat(pedido.getTanda()).isSameAs(a);
        HistorialTandaPedido evento = eventoGuardado();
        assertThat(evento.getIdTandaAnterior()).isNull();
        assertThat(evento.getIdTandaNueva()).isEqualTo(1L);
        assertThat(evento.getNombreTandaNueva()).isEqualTo("A");
        assertThat(evento.getUsuario()).isSameAs(actor);
        assertThat(evento.getSesion().getId()).isEqualTo(500L);
        assertThat(evento.getSesion().getNota()).isEqualTo("Semana 41");
        assertThat(evento.getSnapshotPorcentajePagado()).isEqualTo(62.5f);
        assertThat(evento.getSnapshotPrioridadAutomatica()).isEqualTo(3);
        assertThat(evento.getSnapshotEstadoPedido()).isEqualTo(EstadoPedido.SENADO);
        assertThat(evento.isSnapshotDisenoCompleto()).isTrue();
        assertThat(evento.isSnapshotTallesCompletos()).isFalse();
        assertThat(evento.isSnapshotPagoSuficiente()).isTrue();
    }

    @Test
    void renombrarLaTandaDespuesNoAlteraElHistorial() {
        Tanda a = tanda(1, "A", 1);
        Pedido pedido = pedido(1, EstadoPedido.LISTO_PARA_PRODUCCION, false, null);
        service.aplicar(sesion(mover(pedido, null, existente(a), null)), ID_ACTOR);
        HistorialTandaPedido evento = eventoGuardado();

        a.setNombre("Z");

        assertThat(evento.getNombreTandaNueva()).isEqualTo("A");
        assertThat(evento.getIdTandaNueva()).isEqualTo(1L);
    }

    // ---------- Rechazos ----------

    @Test
    void noEntranPedidosEntregadosNiCancelados() {
        Tanda a = tanda(1, "A", 1);
        Pedido entregado = pedido(1, EstadoPedido.ENTREGADO, true, null);
        Pedido cancelado = pedido(2, EstadoPedido.CANCELADO, false, null);

        assertThatThrownBy(() -> service.aplicar(sesion(mover(entregado, null, existente(a), null)), ID_ACTOR))
            .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.aplicar(sesion(mover(cancelado, null, existente(a), null)), ID_ACTOR))
            .isInstanceOf(BusinessRuleException.class);
        verificarQueNoSeGuardoNada();
    }

    @Test
    void aUnaTandaEnProduccionNoSeLeAgreganPedidos() {
        Tanda a = tanda(1, "A", 1);
        pedido(1, EstadoPedido.EN_PRODUCCION, true, a);
        Pedido suelto = pedido(2, EstadoPedido.LISTO_PARA_PRODUCCION, false, null);

        assertThatThrownBy(() -> service.aplicar(sesion(mover(suelto, null, existente(a), null)), ID_ACTOR))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("en producción");
        verificarQueNoSeGuardoNada();
    }

    @Test
    void deUnaTandaEnProduccionSoloSaleUnPedidoSinEtapasCompletadas() {
        Tanda a = tanda(1, "A", 1);
        Pedido empezado = pedido(1, EstadoPedido.EN_PRODUCCION, true, a);
        Pedido sinEmpezar = pedido(2, EstadoPedido.LISTO_PARA_PRODUCCION, false, a);

        assertThatThrownBy(() -> service.aplicar(sesion(mover(empezado, a, null, "Falta tela")), ID_ACTOR))
            .isInstanceOf(BusinessRuleException.class);
        verificarQueNoSeGuardoNada();

        service.aplicar(sesion(mover(sinEmpezar, a, null, "Falta tela")), ID_ACTOR);
        assertThat(sinEmpezar.getTanda()).isNull();
        assertThat(eventoGuardado().getMotivo()).isEqualTo("Falta tela");
    }

    @Test
    void unaTandaCerradaNoSeModifica() {
        Tanda a = tanda(1, "A", 1);
        Tanda b = tanda(2, "B", 2);
        Pedido entregado = pedido(1, EstadoPedido.ENTREGADO, true, a);
        Pedido suelto = pedido(2, EstadoPedido.LISTO_PARA_PRODUCCION, false, null);

        assertThatThrownBy(() -> service.aplicar(sesion(mover(suelto, null, existente(a), null)), ID_ACTOR))
            .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.aplicar(sesion(mover(entregado, a, existente(b), "Error")), ID_ACTOR))
            .isInstanceOf(BusinessRuleException.class);
        verificarQueNoSeGuardoNada();
    }

    @Test
    void sacarUnPedidoDeSuTandaExigeMotivo() {
        Tanda a = tanda(1, "A", 1);
        Tanda b = tanda(2, "B", 2);
        Pedido pedido = pedido(1, EstadoPedido.LISTO_PARA_PRODUCCION, false, a);

        assertThatThrownBy(() -> service.aplicar(sesion(mover(pedido, a, null, "  ")), ID_ACTOR))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("motivo");
        // Cambiar de tanda también es salir de la anterior.
        assertThatThrownBy(() -> service.aplicar(sesion(mover(pedido, a, existente(b), null)), ID_ACTOR))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("motivo");
        verificarQueNoSeGuardoNada();
        assertThat(pedido.getTanda()).isSameAs(a);
    }

    @Test
    void seRechazaSiElPedidoCambioDeTandaMientrasSePriorizaba() {
        Tanda a = tanda(1, "A", 1);
        Tanda b = tanda(2, "B", 2);
        Pedido pedido = pedido(1, EstadoPedido.LISTO_PARA_PRODUCCION, false, b);

        // El popup lo vio "sin tanda", pero otro administrativo ya lo puso en B.
        assertThatThrownBy(() -> service.aplicar(sesion(mover(pedido, null, existente(a), null)), ID_ACTOR))
            .isInstanceOf(BusinessRuleException.class);
        verificarQueNoSeGuardoNada();
    }

    @Test
    void siFallaUnMovimientoAMitadDeLaSesionNoSeGuardaNada() {
        Tanda a = tanda(1, "A", 1);
        Pedido valido = pedido(1, EstadoPedido.LISTO_PARA_PRODUCCION, false, null);
        Pedido entregado = pedido(2, EstadoPedido.ENTREGADO, true, null);
        Pedido otroValido = pedido(3, EstadoPedido.SENADO, false, null);

        PriorizacionRequest request = new PriorizacionRequest(
            "Semana 41",
            List.of(new TandaNuevaRequest("t1", "B")),
            null,
            List.of(
                mover(valido, null, existente(a), null),
                mover(entregado, null, nueva("t1"), null),
                mover(otroValido, null, nueva("t1"), null)));

        assertThatThrownBy(() -> service.aplicar(request, ID_ACTOR)).isInstanceOf(BusinessRuleException.class);

        verificarQueNoSeGuardoNada();
        assertThat(valido.getTanda()).isNull();
        assertThat(otroValido.getTanda()).isNull();
        assertThat(tandas).extracting(Tanda::getNombre).containsExactly("A");
    }

    @Test
    void unaSesionSinCambiosSeRechaza() {
        tanda(1, "A", 1);

        assertThatThrownBy(() -> service.aplicar(new PriorizacionRequest(" ", null, null, null), ID_ACTOR))
            .isInstanceOf(BusinessRuleException.class);
        verificarQueNoSeGuardoNada();
    }

    @Test
    void soloElAdministrativoPuedePriorizar() {
        Tanda a = tanda(1, "A", 1);
        Pedido pedido = pedido(1, EstadoPedido.LISTO_PARA_PRODUCCION, false, null);
        doThrow(new ForbiddenException("sin permiso")).when(autorizacionService).verificarRolAdministrativo(ID_ACTOR);

        assertThatThrownBy(() -> service.aplicar(sesion(mover(pedido, null, existente(a), null)), ID_ACTOR))
            .isInstanceOf(ForbiddenException.class);
        verificarQueNoSeGuardoNada();
        assertThat(pedido.getTanda()).isNull();
    }

    // ---------- Tandas nuevas y orden ----------

    @Test
    void unaTandaNuevaAceptaPedidosQueYaTienenEtapasCompletadas() {
        Pedido empezado = pedido(1, EstadoPedido.EN_PRODUCCION, true, null);

        service.aplicar(new PriorizacionRequest(null, List.of(new TandaNuevaRequest("t1", "a")), null,
            List.of(mover(empezado, null, nueva("t1"), null))), ID_ACTOR);

        assertThat(empezado.getTanda().getNombre()).isEqualTo("A");
        assertThat(empezado.getTanda().getCreadoPor()).isSameAs(actor);
        HistorialTandaPedido evento = eventoGuardado();
        assertThat(evento.getIdTandaNueva()).isEqualTo(empezado.getTanda().getId()).isNotNull();
    }

    @Test
    void sinOrdenExplicitoLaTandaNuevaEntraEnSuLugarAlfabetico() {
        Tanda a = tanda(1, "A", 1);
        Tanda c = tanda(2, "C", 2);

        service.aplicar(new PriorizacionRequest(null, List.of(new TandaNuevaRequest("t1", "B")), null, null), ID_ACTOR);

        Tanda b = tandas.stream().filter(t -> t.getNombre().equals("B")).findFirst().orElseThrow();
        assertThat(a.getOrden()).isLessThan(b.getOrden());
        assertThat(b.getOrden()).isLessThan(c.getOrden());
    }

    @Test
    void reordenarQuedaRegistradoEnLaSesion() {
        Tanda a = tanda(1, "A", 1);
        Tanda b = tanda(2, "B", 2);

        service.aplicar(new PriorizacionRequest(null, null, List.of(existente(b), existente(a)), null), ID_ACTOR);

        assertThat(b.getOrden()).isLessThan(a.getOrden());
        ArgumentCaptor<SesionPriorizacion> captor = ArgumentCaptor.forClass(SesionPriorizacion.class);
        verify(sesionPriorizacionRepository).save(captor.capture());
        assertThat(captor.getValue().getOrdenAnteriorJson())
            .isEqualTo("[{\"idTanda\":1,\"nombre\":\"A\"},{\"idTanda\":2,\"nombre\":\"B\"}]");
        assertThat(captor.getValue().getOrdenNuevoJson())
            .isEqualTo("[{\"idTanda\":2,\"nombre\":\"B\"},{\"idTanda\":1,\"nombre\":\"A\"}]");
    }

    @Test
    void elOrdenTieneQueIncluirTodasLasTandasAbiertas() {
        Tanda a = tanda(1, "A", 1);
        tanda(2, "B", 2);

        assertThatThrownBy(() -> service.aplicar(new PriorizacionRequest(null, null, List.of(existente(a)), null), ID_ACTOR))
            .isInstanceOf(BusinessRuleException.class);
        verificarQueNoSeGuardoNada();
    }

    @Test
    void noSePuedeCrearUnaTandaConElNombreDeOtraAbierta() {
        tanda(1, "A", 1);

        assertThatThrownBy(() -> service.aplicar(
            new PriorizacionRequest(null, List.of(new TandaNuevaRequest("t1", "A")), null, null), ID_ACTOR))
            .isInstanceOf(BusinessRuleException.class);
        verificarQueNoSeGuardoNada();
    }

    // ---------- Cancelación ----------

    @Test
    void cancelarSacaAlPedidoDeSuTandaAunqueEsteEnProduccion() {
        Tanda a = tanda(1, "A", 1);
        Pedido pedido = pedido(1, EstadoPedido.EN_PRODUCCION, true, a);

        service.sacarPorCancelacion(pedido, actor);

        assertThat(pedido.getTanda()).isNull();
        HistorialTandaPedido evento = eventoGuardado();
        assertThat(evento.getIdTandaAnterior()).isEqualTo(1L);
        assertThat(evento.getNombreTandaAnterior()).isEqualTo("A");
        assertThat(evento.getIdTandaNueva()).isNull();
        assertThat(evento.getSesion()).isNull();
        assertThat(evento.getMotivo()).isEqualTo(PriorizacionTandaService.MOTIVO_CANCELACION);
        assertThat(evento.getSnapshotEstadoPedido()).isEqualTo(EstadoPedido.EN_PRODUCCION);
    }

    @Test
    void cancelarUnPedidoSinTandaNoRegistraNada() {
        Pedido pedido = pedido(1, EstadoPedido.SENADO, false, null);

        service.sacarPorCancelacion(pedido, actor);

        verify(historialTandaPedidoRepository, times(0)).save(any());
    }
}
