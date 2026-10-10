package com.weclover.backend.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.pedido.NotaPedidoResponse;
import com.weclover.backend.dto.produccion.ProduccionEtapaResponse;
import com.weclover.backend.dto.produccion.ProduccionPedidoResponse;
import com.weclover.backend.dto.produccion.ProduccionProductoResponse;
import com.weclover.backend.dto.produccion.ProduccionTandaResponse;
import com.weclover.backend.dto.tanda.EstadoTanda;
import com.weclover.backend.entity.EtapaProduccion;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.NotaPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoEtapaProduccion;
import com.weclover.backend.repository.NotaPedidoRepository;
import com.weclover.backend.repository.PedidoRepository;
import com.weclover.backend.service.TandaService.TandaCalculada;

import lombok.RequiredArgsConstructor;

/**
 * Listado agregado para la Pantalla de Producción (grilla por pedido con sus productos y
 * etapas anidadas) — no existía como endpoint propio hasta esta entrega, se arma acá
 * combinando EstadoPedidoService (precio/porcentaje/puntaje sugerido), TandaService (tanda,
 * estado y posición) y ProductoEtapaProduccionService (estado visual) para no duplicar esas
 * fórmulas.
 */
@Service
@RequiredArgsConstructor
public class ProduccionService {

    private static final Set<String> ROLES_PRODUCCION = Set.of("ROLE_ADMINISTRATIVO", "ROLE_PLANTA");

    private final PedidoRepository pedidoRepository;
    private final NotaPedidoRepository notaPedidoRepository;
    private final EstadoPedidoService estadoPedidoService;
    private final ProductoEtapaProduccionService productoEtapaProduccionService;
    private final TandaService tandaService;
    private final AutorizacionService autorizacionService;

    /**
     * Orden de la respuesta: primero los pedidos de las tandas abiertas, en orden de cola;
     * después los de tandas cerradas; al final los "sin tanda", ordenados por puntaje sugerido.
     *
     * El filtro `estados` no aplica a los pedidos de una tanda abierta: esos se ven siempre
     * (una tanda puede tener pedidos PRESUPUESTADO/SENADO y esconderlos falsearía la tanda). El
     * resto de los filtros sí aplica a todos.
     *
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

        Map<Long, TandaCalculada> tandaPorIdPedido = new HashMap<>();
        Map<Long, Pedido> pedidosPorId = new LinkedHashMap<>();
        for (TandaCalculada calculada : tandaService.calcularTandas()) {
            for (Pedido pedido : calculada.pedidos()) {
                tandaPorIdPedido.put(pedido.getId(), calculada);
                if (calculada.estado() != EstadoTanda.CERRADA) {
                    pedidosPorId.put(pedido.getId(), pedido);
                }
            }
        }
        List<Pedido> porEstado = (estados == null || estados.isEmpty())
            ? pedidoRepository.findAll()
            : pedidoRepository.findByEstadoActualIn(estados);
        porEstado.forEach(pedido -> pedidosPorId.putIfAbsent(pedido.getId(), pedido));

        // El puntaje sugerido es un rank entre TODOS los pedidos activos del sistema, no solo
        // los que van a quedar después de aplicar los filtros de esta consulta — se calcula una
        // sola vez, sin depender de `estados`/`pagoMin`/`pagoMax`/`etapaPendiente`.
        Map<Long, Integer> prioridadesAutomaticas = estadoPedidoService.calcularPrioridadesAutomaticas();
        Map<Long, List<NotaPedido>> notasPorIdPedido = pedidosPorId.isEmpty()
            ? Map.of()
            : notaPedidoRepository.findByPedidoIdInOrderByFechaDescIdDesc(pedidosPorId.keySet()).stream()
                .collect(Collectors.groupingBy(nota -> nota.getPedido().getId()));

        return pedidosPorId.values().stream()
            .map(pedido -> construirRespuesta(
                pedido, prioridadesAutomaticas, tandaPorIdPedido.get(pedido.getId()),
                notasPorIdPedido.getOrDefault(pedido.getId(), List.of())))
            .filter(r -> pagoMin == null || r.porcentajePagado() >= pagoMin)
            .filter(r -> pagoMax == null || r.porcentajePagado() <= pagoMax)
            .filter(r -> etapaPendiente == null || tieneEtapaPendiente(r, etapaPendiente))
            .sorted(ORDEN_GRILLA)
            .toList();
    }

    /** 0 = tanda abierta, 1 = tanda cerrada, 2 = sin tanda. */
    private static int grupo(ProduccionPedidoResponse pedido) {
        if (pedido.tanda() == null) {
            return 2;
        }
        return pedido.tanda().posicion() != null ? 0 : 1;
    }

    private static Integer posicionTanda(ProduccionPedidoResponse pedido) {
        return pedido.tanda() != null ? pedido.tanda().posicion() : null;
    }

    private static Long idTanda(ProduccionPedidoResponse pedido) {
        return pedido.tanda() != null ? pedido.tanda().id() : null;
    }

    private static final Comparator<Integer> ENTEROS_NULL_AL_FINAL = Comparator.nullsLast(Comparator.naturalOrder());
    private static final Comparator<Long> IDS_NULL_AL_FINAL = Comparator.nullsLast(Comparator.naturalOrder());

    private static final Comparator<ProduccionPedidoResponse> ORDEN_GRILLA = Comparator
        .comparingInt(ProduccionService::grupo)
        .thenComparing(ProduccionService::posicionTanda, ENTEROS_NULL_AL_FINAL)
        .thenComparing(ProduccionService::idTanda, IDS_NULL_AL_FINAL)
        .thenComparing(ProduccionPedidoResponse::prioridadAutomatica, ENTEROS_NULL_AL_FINAL)
        .thenComparing(ProduccionPedidoResponse::id);

    private boolean tieneEtapaPendiente(ProduccionPedidoResponse pedido, EtapaProduccion etapaPendiente) {
        return pedido.productos().stream()
            .filter(producto -> !producto.esBandera())
            .flatMap(producto -> producto.etapas().stream())
            .anyMatch(etapa -> etapa.etapa() == etapaPendiente && etapa.aplica() && !etapa.completado());
    }

    private ProduccionPedidoResponse construirRespuesta(
            Pedido pedido, Map<Long, Integer> prioridadesAutomaticas, TandaCalculada tanda, List<NotaPedido> notas) {
        List<ProduccionProductoResponse> productos = new ArrayList<>();
        pedido.getProductos().forEach(producto -> productos.add(construirProducto(producto)));

        ProduccionTandaResponse tandaResponse = tanda == null ? null : new ProduccionTandaResponse(
            tanda.tanda().getId(), tanda.tanda().getNombre(), tanda.posicion(), tanda.estado());
        NotaPedidoResponse ultimaNota = notas.isEmpty() ? null : PedidoSeguimientoService.aRespuesta(notas.get(0));

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
            tandaResponse,
            pedido.getUbicacionActual(),
            ultimaNota,
            notas.size(),
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
