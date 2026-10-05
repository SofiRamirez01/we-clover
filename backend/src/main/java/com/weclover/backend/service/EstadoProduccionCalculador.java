package com.weclover.backend.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.EstadoProduccion;
import com.weclover.backend.entity.EtapaProduccion;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoEtapaProduccion;

/**
 * Cálculo puro (sin repositorios) del EstadoProduccion de un Producto. Estático por el mismo
 * motivo que EtapaProduccionAplicabilidad: evitar ciclos entre los servicios que lo necesitan.
 *
 * Reglas (acordadas con el negocio):
 * - Bandera: null (no participa de producción ni de reportes).
 * - Pedido ENTREGADO: ENTREGADO, sin importar las etapas.
 * - Todas las etapas aplicables completas (incluida CONTROL): TERMINADO.
 * - Si no: la etapa aplicable completada de mayor orden de pipeline (las etapas se pueden marcar
 *   en cualquier orden), sin contar CONTROL; PENDIENTE si no hay ninguna.
 *
 * Solo se miran las etapas APLICABLES: si un producto tenía ESTAMPADO/OJAL y dejó de aplicarle
 * (se quitó el insumo "Estampado" o cambió el tipo de prenda), esa fila vieja no cuenta.
 */
public final class EstadoProduccionCalculador {

    private EstadoProduccionCalculador() {
    }

    public static EstadoProduccion calcular(Producto producto) {
        if (EtapaProduccionAplicabilidad.esBandera(producto)) {
            return null;
        }
        if (producto.getPedido() != null && producto.getPedido().getEstadoActual() == EstadoPedido.ENTREGADO) {
            return EstadoProduccion.ENTREGADO;
        }

        List<EtapaProduccion> aplicables = EtapaProduccionAplicabilidad.etapasAplicables(producto);
        Map<EtapaProduccion, Boolean> completadoPorEtapa = producto.getEtapas().stream()
            .collect(Collectors.toMap(ProductoEtapaProduccion::getEtapa, ProductoEtapaProduccion::isCompletado, (a, b) -> a || b));

        boolean todasCompletas = !aplicables.isEmpty()
            && aplicables.stream().allMatch(etapa -> Boolean.TRUE.equals(completadoPorEtapa.get(etapa)));
        if (todasCompletas) {
            return EstadoProduccion.TERMINADO;
        }

        EstadoProduccion estado = EstadoProduccion.PENDIENTE;
        for (EtapaProduccion etapa : aplicables) {
            if (etapa != EtapaProduccion.CONTROL && Boolean.TRUE.equals(completadoPorEtapa.get(etapa))) {
                estado = estadoAlCompletar(etapa);
            }
        }
        return estado;
    }

    private static EstadoProduccion estadoAlCompletar(EtapaProduccion etapa) {
        return switch (etapa) {
            case CORTE -> EstadoProduccion.CORTADO;
            case ESTAMPADO -> EstadoProduccion.ESTAMPADO;
            case BORDADO -> EstadoProduccion.BORDADO;
            case CONFECCION -> EstadoProduccion.CONFECCION;
            case APODO -> EstadoProduccion.APODO;
            case OJAL -> EstadoProduccion.OJAL;
            case CONTROL -> EstadoProduccion.TERMINADO;
        };
    }
}
