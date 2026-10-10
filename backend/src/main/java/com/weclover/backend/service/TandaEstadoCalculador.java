package com.weclover.backend.service;

import java.util.List;

import com.weclover.backend.dto.tanda.EstadoTanda;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.ProductoEtapaProduccion;

/**
 * Cálculo puro (sin repositorios) del estado derivado de una Tanda a partir de sus pedidos —
 * mismo criterio que EstadoProduccionCalculador: nada de esto se persiste.
 *
 * Como es derivado, una tanda puede "volver atrás" sola: si se destilda la única etapa
 * completada vuelve a PLANIFICADA, y si un pedido sale de ENTREGADO la tanda deja de estar
 * CERRADA.
 */
public final class TandaEstadoCalculador {

    private TandaEstadoCalculador() {
    }

    public static EstadoTanda calcular(List<Pedido> pedidosDeLaTanda) {
        boolean todosFinalizados = !pedidosDeLaTanda.isEmpty()
            && pedidosDeLaTanda.stream().allMatch(TandaEstadoCalculador::estaFinalizado);
        if (todosFinalizados) {
            return EstadoTanda.CERRADA;
        }
        boolean algunoEmpezado = pedidosDeLaTanda.stream().anyMatch(TandaEstadoCalculador::tieneEtapaCompletada);
        return algunoEmpezado ? EstadoTanda.EN_PRODUCCION : EstadoTanda.PLANIFICADA;
    }

    /** Mismo criterio que EstadoPedidoService.cumpleEnProduccion: cualquier etapa tildada de
     *  cualquier prenda habilitada del pedido (Pedido.productos ya excluye las dadas de baja). */
    public static boolean tieneEtapaCompletada(Pedido pedido) {
        return pedido.getProductos().stream()
            .flatMap(producto -> producto.getEtapas().stream())
            .anyMatch(ProductoEtapaProduccion::isCompletado);
    }

    private static boolean estaFinalizado(Pedido pedido) {
        return pedido.getEstadoActual() == EstadoPedido.ENTREGADO
            || pedido.getEstadoActual() == EstadoPedido.CANCELADO;
    }
}
