package com.weclover.backend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.pedido.ContadorEstadoPedidoResponse;
import com.weclover.backend.dto.pedido.FiltrosListadoPedidos;
import com.weclover.backend.dto.pedido.ListadoPedidosResponse;
import com.weclover.backend.dto.pedido.OpcionMolderiaResponse;
import com.weclover.backend.dto.pedido.OpcionesFiltroPedidosResponse;
import com.weclover.backend.dto.pedido.PedidosPorEstado;
import com.weclover.backend.dto.pedido.UnidadesPorEstadoYTipoPrenda;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.repository.OrdenListadoPedidos;
import com.weclover.backend.repository.PedidoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Listado de Base Ventas filtrado y paginado en el servidor (M5.4): lo usan la pestaña Pedidos
 * (en curso) y la pestaña Histórico (todas las promociones, para reutilizar lo vendido en
 * ventas futuras). La promoción de un pedido es el año de su Nº de ficha (2026-05 →
 * promoción 2026; el formato AAAA-NN lo garantiza PedidoCreateRequest/PedidoUpdateRequest).
 *
 * Es información interna: se rechaza a ROLE_CLIENTE y a quien no se identifique. Un
 * ROLE_VENDEDOR solo ve los pedidos que vendió él (Pedido.creadoPor), en el listado, en los
 * contadores y en las opciones de los filtros; el resto de los roles internos ve todos.
 */
@Service
@RequiredArgsConstructor
public class PedidoListadoService {

    static final String ROL_VENDEDOR = "ROLE_VENDEDOR";
    static final int TAMANIO_POR_DEFECTO = 50;
    static final int TAMANIO_MAXIMO = 100;
    /** Lo que la pestaña Pedidos oculta por defecto ("solo activos"). */
    static final List<EstadoPedido> ESTADOS_CERRADOS = List.of(EstadoPedido.ENTREGADO, EstadoPedido.CANCELADO);

    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;
    private final AutorizacionService autorizacionService;

    @Transactional(readOnly = true)
    public ListadoPedidosResponse listar(FiltrosListadoPedidos filtros, Long idUsuarioActor) {
        Long idVendedor = vendedorVisible(idUsuarioActor);
        validar(filtros);

        int pagina = filtros.pagina() != null ? filtros.pagina() : 0;
        int tamanio = filtros.tamanio() != null ? filtros.tamanio() : TAMANIO_POR_DEFECTO;
        String prefijoPromocion = prefijoPromocion(filtros.promocion());

        Page<Pedido> resultado = pedidoRepository.buscarListado(
            prefijoPromocion,
            filtros.desde(),
            filtros.hasta(),
            idVendedor,
            patronBusqueda(filtros.buscar()),
            filtros.estado(),
            Boolean.TRUE.equals(filtros.soloActivos()),
            ESTADOS_CERRADOS,
            filtros.idPatronCorte(),
            filtros.idTipoPrenda(),
            PageRequest.of(pagina, tamanio, OrdenListadoPedidos.de(filtros.orden(), filtros.direccion())));

        List<ContadorEstadoPedidoResponse> contadores = contadores(
            prefijoPromocion, filtros.desde(), filtros.hasta(), idVendedor);

        return new ListadoPedidosResponse(
            pedidoService.construirRespuestas(resultado.getContent()),
            resultado.getTotalElements(),
            pagina,
            tamanio,
            resultado.hasNext(),
            contadores);
    }

    @Transactional(readOnly = true)
    public OpcionesFiltroPedidosResponse opcionesFiltro(Long idUsuarioActor) {
        Long idVendedor = vendedorVisible(idUsuarioActor);

        List<Integer> promociones = pedidoRepository.listarPrefijosPromocion(idVendedor).stream()
            .filter(prefijo -> prefijo != null && prefijo.matches("\\d{4}"))
            .map(Integer::valueOf)
            .distinct()
            .sorted(Comparator.reverseOrder())
            .toList();

        List<OpcionMolderiaResponse> molderias = pedidoRepository.listarMolderiasUsadas(idVendedor).stream()
            .sorted(Comparator.comparing(OpcionMolderiaResponse::tipoPrenda)
                .thenComparing(OpcionMolderiaResponse::numeroInterno)
                .thenComparing(OpcionMolderiaResponse::nombrePatronCorte))
            .toList();

        return new OpcionesFiltroPedidosResponse(promociones, molderias);
    }

    /** null = ve todos los pedidos; un id = solo los que vendió ese vendedor. */
    private Long vendedorVisible(Long idUsuarioActor) {
        Usuario actor = autorizacionService.obtenerActorInterno(idUsuarioActor);
        return ROL_VENDEDOR.equals(actor.getRol().getNombre()) ? actor.getId() : null;
    }

    private void validar(FiltrosListadoPedidos filtros) {
        if (filtros.promocion() != null && (filtros.promocion() < 1000 || filtros.promocion() > 9999)) {
            throw new BusinessRuleException("La promoción tiene que ser un año de 4 dígitos (ej: 2026)");
        }
        if ((filtros.idPatronCorte() == null) != (filtros.idTipoPrenda() == null)) {
            throw new BusinessRuleException(
                "El filtro de moldería necesita la moldería y el tipo de prenda juntos");
        }
        if (filtros.desde() != null && filtros.hasta() != null && filtros.desde().isAfter(filtros.hasta())) {
            throw new BusinessRuleException("La fecha 'desde' no puede ser posterior a la fecha 'hasta'");
        }
        if (filtros.pagina() != null && filtros.pagina() < 0) {
            throw new BusinessRuleException("La página no puede ser negativa");
        }
        if (filtros.tamanio() != null && (filtros.tamanio() < 1 || filtros.tamanio() > TAMANIO_MAXIMO)) {
            throw new BusinessRuleException("El tamaño de página tiene que estar entre 1 y " + TAMANIO_MAXIMO);
        }
    }

    /** Todos los estados en el orden del enum, con 0 los que no tienen pedidos. */
    private List<ContadorEstadoPedidoResponse> contadores(
            String prefijoPromocion, LocalDate desde, LocalDate hasta, Long idVendedor) {
        Map<EstadoPedido, Long> pedidosPorEstado = new EnumMap<>(EstadoPedido.class);
        for (PedidosPorEstado fila : pedidoRepository.contarPorEstado(prefijoPromocion, desde, hasta, idVendedor)) {
            pedidosPorEstado.put(fila.estado(), fila.pedidos());
        }

        Map<EstadoPedido, Map<String, Long>> unidades = new EnumMap<>(EstadoPedido.class);
        for (UnidadesPorEstadoYTipoPrenda fila
                : pedidoRepository.sumarUnidadesPorEstadoYTipoPrenda(prefijoPromocion, desde, hasta, idVendedor)) {
            unidades.computeIfAbsent(fila.estado(), estado -> new LinkedHashMap<>())
                .put(fila.tipoPrenda(), fila.unidades() != null ? fila.unidades() : 0L);
        }

        List<ContadorEstadoPedidoResponse> contadores = new ArrayList<>();
        for (EstadoPedido estado : EstadoPedido.values()) {
            contadores.add(new ContadorEstadoPedidoResponse(
                estado,
                pedidosPorEstado.getOrDefault(estado, 0L),
                unidades.getOrDefault(estado, Map.of())));
        }
        return contadores;
    }

    /** "2026" → "2026-%" (LIKE sobre el Nº de ficha); null = todas las promociones. */
    static String prefijoPromocion(Integer promocion) {
        return promocion != null ? promocion + "-%" : null;
    }

    /** Texto libre → patrón LIKE "%texto%" con %, _ y ! escapados ('!' es el ESCAPE de la
     *  consulta); null si viene vacío. */
    static String patronBusqueda(String buscar) {
        if (buscar == null || buscar.isBlank()) {
            return null;
        }
        String escapado = buscar.trim()
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_");
        return "%" + escapado + "%";
    }
}
