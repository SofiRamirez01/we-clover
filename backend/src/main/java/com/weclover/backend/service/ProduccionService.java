package com.weclover.backend.service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.produccion.ProduccionEtapaResponse;
import com.weclover.backend.dto.produccion.ProduccionPedidoResponse;
import com.weclover.backend.dto.produccion.ProduccionProductoResponse;
import com.weclover.backend.entity.EtapaProduccion;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoEtapaProduccion;
import com.weclover.backend.repository.PedidoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Listado agregado para la Pantalla de Producción (grilla por pedido con sus productos y
 * etapas anidadas) — no existía como endpoint propio hasta esta entrega, se arma acá
 * combinando EstadoPedidoService (precio/porcentaje/prioridad) y
 * ProductoEtapaProduccionService (estado visual) para no duplicar esas fórmulas.
 */
@Service
@RequiredArgsConstructor
public class ProduccionService {

    private static final Set<String> ROLES_PRODUCCION = Set.of("ROLE_ADMINISTRATIVO", "ROLE_PLANTA");

    private final PedidoRepository pedidoRepository;
    private final EstadoPedidoService estadoPedidoService;
    private final ProductoEtapaProduccionService productoEtapaProduccionService;
    private final AutorizacionService autorizacionService;

    /**
     * No es readOnly: sincroniza (crea si faltan) las filas de ProductoEtapaProduccion de cada
     * producto no-Bandera antes de armar la respuesta (ver
     * ProductoEtapaProduccionService.sincronizarEtapas) — evita mostrar una etapa recién vuelta
     * aplicable (ej. se agregó un insumo "Estampado") como si no existiera. Es una operación
     * idempotente y de costo marginal cuando ya está todo sincronizado (el caso normal).
     */
    @Transactional
    public List<ProduccionPedidoResponse> listar(
            List<EstadoPedido> estados, EtapaProduccion etapaPendiente, Float pagoMin, Float pagoMax,
            Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_PRODUCCION);

        List<Pedido> pedidos = (estados == null || estados.isEmpty())
            ? pedidoRepository.findAll()
            : pedidoRepository.findByEstadoActualIn(estados);

        // La prioridad automática es un rank entre TODOS los pedidos activos del sistema, no
        // solo los que van a quedar después de aplicar los filtros de esta consulta — se
        // calcula una sola vez, sin depender de `estados`/`pagoMin`/`pagoMax`/`etapaPendiente`.
        Map<Long, Integer> prioridadesAutomaticas = estadoPedidoService.calcularPrioridadesAutomaticas();

        return pedidos.stream()
            .map(pedido -> construirRespuesta(pedido, prioridadesAutomaticas))
            .filter(r -> pagoMin == null || r.porcentajePagado() >= pagoMin)
            .filter(r -> pagoMax == null || r.porcentajePagado() <= pagoMax)
            .filter(r -> etapaPendiente == null || tieneEtapaPendiente(r, etapaPendiente))
            .sorted(Comparator.comparing(ProduccionService::prioridadEfectiva, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    private static Integer prioridadEfectiva(ProduccionPedidoResponse pedido) {
        return pedido.prioridadManual() != null ? pedido.prioridadManual() : pedido.prioridadAutomatica();
    }

    private boolean tieneEtapaPendiente(ProduccionPedidoResponse pedido, EtapaProduccion etapaPendiente) {
        return pedido.productos().stream()
            .filter(producto -> !producto.esBandera())
            .flatMap(producto -> producto.etapas().stream())
            .anyMatch(etapa -> etapa.etapa() == etapaPendiente && etapa.aplica() && !etapa.completado());
    }

    private ProduccionPedidoResponse construirRespuesta(Pedido pedido, Map<Long, Integer> prioridadesAutomaticas) {
        List<ProduccionProductoResponse> productos = pedido.getProductos().stream()
            .map(this::construirProducto)
            .toList();

        return new ProduccionPedidoResponse(
            pedido.getId(),
            pedido.getCodigoInterno(),
            pedido.getColegio().getNombre(),
            pedido.getCurso(),
            pedido.getEstadoActual(),
            pedido.getFechaVenta(),
            pedido.getFechaEstimadaEntrega(),
            estadoPedidoService.calcularPorcentajePagado(pedido),
            prioridadesAutomaticas.get(pedido.getId()),
            pedido.getPrioridadManual(),
            productos
        );
    }

    private ProduccionProductoResponse construirProducto(Producto producto) {
        String tipoPrenda = producto.getTipoPrenda() != null ? producto.getTipoPrenda().getNombre() : null;

        if (EtapaProduccionAplicabilidad.esBandera(producto)) {
            return new ProduccionProductoResponse(
                producto.getId(), tipoPrenda, producto.getCantidadTotal(), true,
                null, List.of(),
                producto.getEstadoBandera(), producto.getFechaPedidoProveedor(), producto.getFechaRecibido()
            );
        }

        productoEtapaProduccionService.sincronizarEtapas(producto);

        List<EtapaProduccion> aplicables = EtapaProduccionAplicabilidad.etapasAplicables(producto);
        Map<EtapaProduccion, ProductoEtapaProduccion> filasPorEtapa = producto.getEtapas().stream()
            .collect(Collectors.toMap(ProductoEtapaProduccion::getEtapa, fila -> fila));

        List<ProduccionEtapaResponse> etapas = Arrays.stream(EtapaProduccion.values())
            .map(etapa -> {
                ProductoEtapaProduccion fila = filasPorEtapa.get(etapa);
                return new ProduccionEtapaResponse(
                    etapa,
                    aplicables.contains(etapa),
                    fila != null && fila.isCompletado(),
                    fila != null ? fila.getFechaCompletado() : null,
                    fila != null && fila.getEmpleado() != null ? fila.getEmpleado().getId() : null,
                    fila != null && fila.getEmpleado() != null ? fila.getEmpleado().getNombre() : null
                );
            })
            .toList();

        String estadoVisual = productoEtapaProduccionService.calcularEstadoVisual(producto.getEtapas());

        return new ProduccionProductoResponse(
            producto.getId(), tipoPrenda, producto.getCantidadTotal(), false,
            estadoVisual, etapas,
            null, null, null
        );
    }
}
