package com.weclover.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.weclover.backend.dto.tanda.EstadoTanda;
import com.weclover.backend.dto.tanda.TandaResponse;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Tanda;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ForbiddenException;
import com.weclover.backend.repository.PedidoRepository;
import com.weclover.backend.repository.TandaRepository;

/** Listado, posición visible, renombrar y eliminar tandas (paso 2 de Tandas). */
@ExtendWith(MockitoExtension.class)
class TandaServiceTest {

    private static final Long ID_ACTOR = 7L;

    @Mock private TandaRepository tandaRepository;
    @Mock private PedidoRepository pedidoRepository;
    @Mock private AutorizacionService autorizacionService;

    @InjectMocks
    private TandaService service;

    private final List<Pedido> pedidosConTanda = new ArrayList<>();

    private Tanda tanda(long id, String nombre, int orden) {
        Tanda tanda = Tanda.builder().id(id).nombre(nombre).orden(orden).build();
        lenient().when(tandaRepository.findById(id)).thenReturn(Optional.of(tanda));
        return tanda;
    }

    private void pedidoEn(Tanda tanda, long idPedido, EstadoPedido estado, boolean etapaCompletada) {
        Pedido pedido = TandaEstadoCalculadorTest.pedido(idPedido, estado, etapaCompletada);
        pedido.setTanda(tanda);
        pedidosConTanda.add(pedido);
    }

    private void dadasLasTandas(Tanda... tandas) {
        lenient().when(tandaRepository.findAllByOrderByOrdenAscIdAsc()).thenReturn(Arrays.asList(tandas));
        lenient().when(pedidoRepository.findByTandaIsNotNull()).thenReturn(pedidosConTanda);
        lenient().when(pedidoRepository.findByTanda_Id(any())).thenAnswer(inv -> pedidosConTanda.stream()
            .filter(pedido -> pedido.getTanda().getId().equals(inv.getArgument(0)))
            .toList());
    }

    @Test
    void laPosicionVisibleCuentaSoloLasTandasNoCerradas() {
        Tanda a = tanda(1, "A", 10);
        Tanda b = tanda(2, "B", 20);
        Tanda c = tanda(3, "C", 30);
        pedidoEn(a, 1, EstadoPedido.EN_PRODUCCION, true);
        pedidoEn(b, 2, EstadoPedido.LISTO_PARA_PRODUCCION, false);
        dadasLasTandas(a, b, c);

        List<TandaResponse> antes = service.listar(false, ID_ACTOR);
        assertThat(antes).extracting(TandaResponse::nombre).containsExactly("A", "B", "C");
        assertThat(antes).extracting(TandaResponse::posicion).containsExactly(1, 2, 3);

        // Se entrega el único pedido de A: se cierra sola y B pasa a ser la tanda 1.
        pedidosConTanda.get(0).setEstadoActual(EstadoPedido.ENTREGADO);

        List<TandaResponse> despues = service.listar(false, ID_ACTOR);
        assertThat(despues).extracting(TandaResponse::nombre).containsExactly("B", "C");
        assertThat(despues).extracting(TandaResponse::posicion).containsExactly(1, 2);
        // El orden guardado no se renumera.
        assertThat(b.getOrden()).isEqualTo(20);
    }

    @Test
    void lasCerradasSoloAparecenSiSePidenYNoTienenPosicion() {
        Tanda a = tanda(1, "A", 10);
        Tanda b = tanda(2, "B", 20);
        pedidoEn(a, 1, EstadoPedido.ENTREGADO, true);
        dadasLasTandas(a, b);

        List<TandaResponse> todas = service.listar(true, ID_ACTOR);

        assertThat(todas).extracting(TandaResponse::estado).containsExactly(EstadoTanda.CERRADA, EstadoTanda.PLANIFICADA);
        assertThat(todas).extracting(TandaResponse::posicion).containsExactly(null, 1);
    }

    @Test
    void listarSumaPedidosYUnidadesPorTipoDePrenda() {
        Tanda a = tanda(1, "A", 10);
        pedidoEn(a, 1, EstadoPedido.LISTO_PARA_PRODUCCION, false);
        pedidoEn(a, 2, EstadoPedido.LISTO_PARA_PRODUCCION, false);
        dadasLasTandas(a);

        TandaResponse respuesta = service.listar(false, ID_ACTOR).get(0);

        assertThat(respuesta.cantidadPedidos()).isEqualTo(2);
        assertThat(respuesta.unidadesPorTipoPrenda()).hasSize(1);
        assertThat(respuesta.unidadesPorTipoPrenda().get(0).unidades()).isEqualTo(40);
    }

    @Test
    void renombrarNormalizaAMayusculas() {
        Tanda a = tanda(1, "A", 10);
        dadasLasTandas(a);

        TandaResponse respuesta = service.renombrar(1L, " d ", ID_ACTOR);

        assertThat(respuesta.nombre()).isEqualTo("D");
        verify(tandaRepository).save(a);
    }

    @Test
    void renombrarRechazaNombresQueNoSonSoloLetras() {
        Tanda a = tanda(1, "A", 10);
        dadasLasTandas(a);

        assertThatThrownBy(() -> service.renombrar(1L, "A1", ID_ACTOR)).isInstanceOf(BusinessRuleException.class);
        verify(tandaRepository, never()).save(any());
    }

    @Test
    void renombrarRechazaUnNombreUsadoPorOtraTandaAbierta() {
        Tanda a = tanda(1, "A", 10);
        Tanda b = tanda(2, "B", 20);
        dadasLasTandas(a, b);

        assertThatThrownBy(() -> service.renombrar(2L, "A", ID_ACTOR)).isInstanceOf(BusinessRuleException.class);
        verify(tandaRepository, never()).save(any());
    }

    @Test
    void elNombreDeUnaTandaCerradaSePuedeReutilizar() {
        Tanda a = tanda(1, "A", 10);
        Tanda b = tanda(2, "B", 20);
        pedidoEn(a, 1, EstadoPedido.ENTREGADO, true);
        dadasLasTandas(a, b);

        assertThat(service.renombrar(2L, "A", ID_ACTOR).nombre()).isEqualTo("A");
    }

    @Test
    void unaTandaCerradaNoSePuedeRenombrar() {
        Tanda a = tanda(1, "A", 10);
        pedidoEn(a, 1, EstadoPedido.ENTREGADO, true);
        dadasLasTandas(a);

        assertThatThrownBy(() -> service.renombrar(1L, "Z", ID_ACTOR)).isInstanceOf(BusinessRuleException.class);
        verify(tandaRepository, never()).save(any());
    }

    @Test
    void soloSePuedeEliminarUnaTandaVacia() {
        Tanda a = tanda(1, "A", 10);
        Tanda b = tanda(2, "B", 20);
        pedidoEn(a, 1, EstadoPedido.LISTO_PARA_PRODUCCION, false);
        dadasLasTandas(a, b);

        assertThatThrownBy(() -> service.eliminar(1L, ID_ACTOR)).isInstanceOf(BusinessRuleException.class);
        verify(tandaRepository, never()).delete(any());

        service.eliminar(2L, ID_ACTOR);
        verify(tandaRepository).delete(b);
    }

    @Test
    void renombrarYEliminarExigenRolAdministrativo() {
        Tanda a = tanda(1, "A", 10);
        dadasLasTandas(a);
        doThrow(new ForbiddenException("sin permiso")).when(autorizacionService).verificarRolAdministrativo(ID_ACTOR);

        assertThatThrownBy(() -> service.renombrar(1L, "B", ID_ACTOR)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> service.eliminar(1L, ID_ACTOR)).isInstanceOf(ForbiddenException.class);

        assertThat(a.getNombre()).isEqualTo("A");
        verify(tandaRepository, never()).save(any());
        verify(tandaRepository, never()).delete(any());
    }

    @Test
    void lasAlertasSonLosPedidosListosQueTodaviaNoTienenTanda() {
        Tanda a = tanda(1, "A", 10);
        Pedido conTanda = TandaEstadoCalculadorTest.pedido(1, EstadoPedido.LISTO_PARA_PRODUCCION, false);
        conTanda.setTanda(a);
        Pedido sinTanda = TandaEstadoCalculadorTest.pedido(2, EstadoPedido.LISTO_PARA_PRODUCCION, false);
        sinTanda.setCodigoInterno("2026-09");
        sinTanda.setColegio(com.weclover.backend.entity.Colegio.builder().nombre("Colegio X").build());
        when(pedidoRepository.findByEstadoActualIn(List.of(EstadoPedido.LISTO_PARA_PRODUCCION)))
            .thenReturn(List.of(conTanda, sinTanda));

        var alertas = service.listarAlertas(ID_ACTOR);

        assertThat(alertas).hasSize(1);
        assertThat(alertas.get(0).idPedido()).isEqualTo(2L);
        assertThat(alertas.get(0).codigoInterno()).isEqualTo("2026-09");
        verify(autorizacionService).verificarRolAdministrativo(ID_ACTOR);
    }

    @Test
    void listarAdmiteAdministrativoYPlanta() {
        dadasLasTandas();

        service.listar(false, ID_ACTOR);

        verify(autorizacionService).verificarRolPermitido(ID_ACTOR, java.util.Set.of("ROLE_ADMINISTRATIVO", "ROLE_PLANTA"));
    }
}
