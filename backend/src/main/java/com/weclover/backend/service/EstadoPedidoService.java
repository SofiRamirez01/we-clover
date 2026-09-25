package com.weclover.backend.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.HistorialEstadoPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.repository.PedidoRepository;
import com.weclover.backend.repository.ProductoEtapaProduccionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Cálculo centralizado del estado de un Pedido y su prioridad, más las fórmulas de
 * precioTotal/porcentajePagado reusadas por PedidoService y PlanificacionCompraService. Vive
 * en su propio servicio (y no en PedidoService, donde uno esperaría encontrarlo) porque
 * recalcularEstadoPedido lo disparan ProductoService, CargaTallesService,
 * ProductoEtapaProduccionService y el propio PedidoService (ver 3.5 de la entrega de
 * Producción) — ponerlo en cualquiera de esos otros servicios hubiera creado una dependencia
 * circular de beans (PedidoService ya depende de ProductoService y CargaTallesService). Por el
 * mismo motivo, la validación de "diseño completo" se resolvió como un método estático puro
 * (ver ProductoDisenoValidador) en vez de llamar a ProductoService desde acá.
 */
@Service
@RequiredArgsConstructor
public class EstadoPedidoService {

    private final PedidoRepository pedidoRepository;
    private final ProductoEtapaProduccionRepository productoEtapaProduccionRepository;

    /** @Lazy: CargaTallesService también depende de este servicio (para disparar el recálculo
     *  al cargar/cerrar talles), así que la inyección directa en ambos sentidos crearía un
     *  ciclo — se rompe con un proxy perezoso de este lado. */
    @Lazy
    private final CargaTallesService cargaTallesService;

    private static final float UMBRAL_PORCENTAJE_PAGADO_LISTO = 50f;

    @Transactional
    public void recalcularEstadoPedido(Long idPedido) {
        Pedido pedido = pedidoRepository.findById(idPedido)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el pedido con id " + idPedido));

        EstadoPedido actual = pedido.getEstadoActual();
        if (actual == EstadoPedido.ENTREGADO || actual == EstadoPedido.CANCELADO) {
            return;
        }

        EstadoPedido nuevo = actual;
        if (nivel(nuevo) < 1 && cumpleListoParaProduccion(pedido)) {
            nuevo = EstadoPedido.LISTO_PARA_PRODUCCION;
        }
        if (nivel(nuevo) >= 1 && nivel(nuevo) < 2 && cumpleEnProduccion(pedido)) {
            nuevo = EstadoPedido.EN_PRODUCCION;
        }
        if (nivel(nuevo) >= 1 && nivel(nuevo) < 3 && cumpleTerminado(pedido)) {
            nuevo = EstadoPedido.TERMINADO;
        }

        if (nuevo != actual) {
            pedido.setEstadoActual(nuevo);
            pedido.getHistorial().add(HistorialEstadoPedido.builder()
                .pedido(pedido)
                .estado(nuevo)
                .fechaCambio(LocalDateTime.now())
                .modificadoPor(null)
                .observaciones("Cambio automático de estado")
                .build());
            pedidoRepository.save(pedido);
        }
    }

    /** 0 = PRESUPUESTADO/SENADO, 1 = LISTO_PARA_PRODUCCION, 2 = EN_PRODUCCION, 3 = TERMINADO.
     *  ENTREGADO/CANCELADO no entran acá (se filtran antes en recalcularEstadoPedido). */
    private int nivel(EstadoPedido estado) {
        return switch (estado) {
            case PRESUPUESTADO, SENADO -> 0;
            case LISTO_PARA_PRODUCCION -> 1;
            case EN_PRODUCCION -> 2;
            case TERMINADO -> 3;
            case ENTREGADO, CANCELADO -> 4;
        };
    }

    private boolean cumpleListoParaProduccion(Pedido pedido) {
        boolean disenoCompleto = pedido.getProductos().stream()
            .allMatch(producto -> ProductoDisenoValidador.motivoDisenoIncompleto(producto).isEmpty());
        boolean tallesCompletos = cargaTallesService.tallesCompletos(pedido);
        boolean pagoSuficiente = calcularPorcentajePagado(pedido) >= UMBRAL_PORCENTAJE_PAGADO_LISTO;
        return disenoCompleto && tallesCompletos && pagoSuficiente;
    }

    private boolean cumpleEnProduccion(Pedido pedido) {
        return productoEtapaProduccionRepository.existsByProducto_Pedido_IdAndCompletadoTrue(pedido.getId());
    }

    private boolean cumpleTerminado(Pedido pedido) {
        List<Producto> noBandera = pedido.getProductos().stream()
            .filter(producto -> !EtapaProduccionAplicabilidad.esBandera(producto))
            .toList();
        if (noBandera.isEmpty()) {
            return false;
        }
        return noBandera.stream().allMatch(this::todasLasEtapasAplicablesCompletas);
    }

    private boolean todasLasEtapasAplicablesCompletas(Producto producto) {
        var aplicables = EtapaProduccionAplicabilidad.etapasAplicables(producto);
        var filas = producto.getEtapas();
        Map<com.weclover.backend.entity.EtapaProduccion, Boolean> completadoPorEtapa = new LinkedHashMap<>();
        filas.forEach(fila -> completadoPorEtapa.put(fila.getEtapa(), fila.isCompletado()));
        return aplicables.stream().allMatch(etapa -> Boolean.TRUE.equals(completadoPorEtapa.get(etapa)));
    }

    /** Misma fórmula que PedidoService.construirRespuesta: suma cantidad×costo de cada
     *  producto, o montoReferenciaImportado si esa suma da 0 (pedidos importados de Excel sin
     *  desglose real por prenda). */
    public float calcularPrecioTotalPedido(Pedido pedido) {
        float total = 0f;
        for (Producto producto : pedido.getProductos()) {
            total += producto.getCantidadTotal() * producto.getCosto();
        }
        if (total <= 0 && pedido.getMontoReferenciaImportado() != null) {
            return pedido.getMontoReferenciaImportado();
        }
        return total;
    }

    public float calcularPorcentajePagado(Pedido pedido) {
        float precioTotal = calcularPrecioTotalPedido(pedido);
        return precioTotal > 0 ? (pedido.getPagoInicial() / precioTotal) * 100 : 0f;
    }

    /** Rank (1 = más prioritario) de cada pedido activo (no ENTREGADO/CANCELADO) por
     *  porcentajePagado descendente. Se calcula una sola vez por request (ver
     *  PedidoService.listarPedidos) en vez de recomputar el ranking completo por cada pedido. */
    public Map<Long, Integer> calcularPrioridadesAutomaticas() {
        List<Pedido> activos = pedidoRepository.findAll().stream()
            .filter(p -> p.getEstadoActual() != EstadoPedido.ENTREGADO && p.getEstadoActual() != EstadoPedido.CANCELADO)
            .sorted(Comparator.comparing(this::calcularPorcentajePagado).reversed())
            .toList();

        Map<Long, Integer> resultado = new LinkedHashMap<>();
        for (int i = 0; i < activos.size(); i++) {
            resultado.put(activos.get(i).getId(), i + 1);
        }
        return resultado;
    }
}
