package com.weclover.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.weclover.backend.dto.tanda.EstadoTanda;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.EtapaProduccion;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoEtapaProduccion;

/** Estado derivado de la tanda (no persistido). */
class TandaEstadoCalculadorTest {

    /** Pedido con una prenda y una etapa CORTE, completada o no. */
    static Pedido pedido(long id, EstadoPedido estado, boolean etapaCompletada) {
        Pedido pedido = Pedido.builder().id(id).estadoActual(estado).productos(new ArrayList<>()).build();
        Producto producto = Producto.builder().id(id * 10).pedido(pedido).cantidadTotal(20).etapas(new ArrayList<>()).build();
        producto.getEtapas().add(ProductoEtapaProduccion.builder()
            .producto(producto).etapa(EtapaProduccion.CORTE).completado(etapaCompletada).build());
        pedido.getProductos().add(producto);
        return pedido;
    }

    @Test
    void planificadaSiNingunPedidoTieneUnaEtapaCompletada() {
        List<Pedido> pedidos = List.of(
            pedido(1, EstadoPedido.LISTO_PARA_PRODUCCION, false),
            pedido(2, EstadoPedido.SENADO, false));

        assertThat(TandaEstadoCalculador.calcular(pedidos)).isEqualTo(EstadoTanda.PLANIFICADA);
    }

    @Test
    void tandaVaciaEsPlanificada() {
        assertThat(TandaEstadoCalculador.calcular(List.of())).isEqualTo(EstadoTanda.PLANIFICADA);
    }

    @Test
    void enProduccionSiAlgunPedidoTieneAlMenosUnaEtapaCompletada() {
        List<Pedido> pedidos = List.of(
            pedido(1, EstadoPedido.LISTO_PARA_PRODUCCION, false),
            pedido(2, EstadoPedido.EN_PRODUCCION, true));

        assertThat(TandaEstadoCalculador.calcular(pedidos)).isEqualTo(EstadoTanda.EN_PRODUCCION);
    }

    @Test
    void cerradaSiTodosLosPedidosEstanEntregadosOCancelados() {
        List<Pedido> pedidos = List.of(
            pedido(1, EstadoPedido.ENTREGADO, true),
            pedido(2, EstadoPedido.CANCELADO, false));

        assertThat(TandaEstadoCalculador.calcular(pedidos)).isEqualTo(EstadoTanda.CERRADA);
    }

    @Test
    void noSeCierraMientrasQuedeUnPedidoSinEntregar() {
        List<Pedido> pedidos = List.of(
            pedido(1, EstadoPedido.ENTREGADO, true),
            pedido(2, EstadoPedido.TERMINADO, true));

        assertThat(TandaEstadoCalculador.calcular(pedidos)).isEqualTo(EstadoTanda.EN_PRODUCCION);
    }
}
