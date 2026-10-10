package com.weclover.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.tanda.AlertaPriorizacionResponse;
import com.weclover.backend.dto.tanda.EstadoTanda;
import com.weclover.backend.dto.tanda.TandaResponse;
import com.weclover.backend.dto.tanda.UnidadesPorTipoPrendaResponse;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.Tanda;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.repository.PedidoRepository;
import com.weclover.backend.repository.TandaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Tandas de producción: grupos de pedidos completos que se producen juntos. Información
 * interna — todos los métodos validan rol (ROLE_PLANTA solo lee; crear, renombrar, reordenar,
 * eliminar y asignar pedidos es exclusivo de ROLE_ADMINISTRATIVO).
 */
@Service
@RequiredArgsConstructor
public class TandaService {

    private static final Set<String> ROLES_LECTURA = Set.of("ROLE_ADMINISTRATIVO", "ROLE_PLANTA");

    /** Solo letras, como en el Excel de origen (A, B… y después de la Z: AA, AB…). */
    private static final String PATRON_NOMBRE = "[A-Z]{1,5}";

    private static final String SIN_TIPO_PRENDA = "Sin tipo";

    private final TandaRepository tandaRepository;
    private final PedidoRepository pedidoRepository;
    private final AutorizacionService autorizacionService;

    /** Tanda con sus pedidos y sus datos derivados, calculados una sola vez por request. */
    record TandaCalculada(Tanda tanda, List<Pedido> pedidos, EstadoTanda estado, Integer posicion) {
    }

    @Transactional(readOnly = true)
    public List<TandaResponse> listar(boolean incluirCerradas, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_LECTURA);

        return calcularTandas().stream()
            .filter(calculada -> incluirCerradas || calculada.estado() != EstadoTanda.CERRADA)
            .map(this::construirRespuesta)
            .toList();
    }

    /** Avisos para quien prioriza (solo ROLE_ADMINISTRATIVO): pedidos que ya están
     *  LISTO_PARA_PRODUCCION y todavía no tienen tanda. Calculado, no persistido. */
    @Transactional(readOnly = true)
    public List<AlertaPriorizacionResponse> listarAlertas(Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);

        return pedidoRepository.findByEstadoActualIn(List.of(EstadoPedido.LISTO_PARA_PRODUCCION)).stream()
            .filter(pedido -> pedido.getTanda() == null)
            .map(pedido -> new AlertaPriorizacionResponse(
                AlertaPriorizacionResponse.Tipo.LISTO_SIN_TANDA,
                pedido.getId(),
                pedido.getCodigoInterno(),
                pedido.getColegio().getNombre(),
                pedido.getCurso()))
            .toList();
    }

    @Transactional
    public TandaResponse renombrar(Long idTanda, String nombre, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);

        List<TandaCalculada> tandas = calcularTandas();
        TandaCalculada actual = buscar(tandas, idTanda);
        if (actual.estado() == EstadoTanda.CERRADA) {
            throw new BusinessRuleException(
                "La tanda " + actual.tanda().getNombre() + " está cerrada y no se puede modificar");
        }

        String nombreNormalizado = normalizarNombre(nombre);
        validarNombreLibre(tandas, nombreNormalizado, idTanda);

        actual.tanda().setNombre(nombreNormalizado);
        tandaRepository.save(actual.tanda());
        return construirRespuesta(actual);
    }

    @Transactional
    public void eliminar(Long idTanda, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);

        Tanda tanda = tandaRepository.findById(idTanda)
            .orElseThrow(() -> new ResourceNotFoundException("No existe la tanda con id " + idTanda));
        if (!pedidoRepository.findByTanda_Id(idTanda).isEmpty()) {
            throw new BusinessRuleException("Solo se puede eliminar una tanda vacía: la tanda "
                + tanda.getNombre() + " todavía tiene pedidos");
        }
        // Borrado físico: el historial no la referencia por FK (ver HistorialTandaPedido).
        tandaRepository.delete(tanda);
    }

    /**
     * Todas las tandas en orden de cola, con su estado derivado y su posición visible. La
     * posición cuenta solo las no cerradas, así que al cerrarse una tanda la siguiente pasa a
     * ser la 1 sin renumerar nada en la base.
     */
    List<TandaCalculada> calcularTandas() {
        Map<Long, List<Pedido>> pedidosPorTanda = pedidoRepository.findByTandaIsNotNull().stream()
            .collect(Collectors.groupingBy(pedido -> pedido.getTanda().getId()));

        List<TandaCalculada> resultado = new ArrayList<>();
        int posicion = 0;
        for (Tanda tanda : tandaRepository.findAllByOrderByOrdenAscIdAsc()) {
            List<Pedido> pedidos = pedidosPorTanda.getOrDefault(tanda.getId(), List.of());
            EstadoTanda estado = TandaEstadoCalculador.calcular(pedidos);
            Integer posicionVisible = estado == EstadoTanda.CERRADA ? null : ++posicion;
            resultado.add(new TandaCalculada(tanda, pedidos, estado, posicionVisible));
        }
        return resultado;
    }

    /** Mayúsculas y sin espacios alrededor; rechaza cualquier cosa que no sean solo letras. */
    static String normalizarNombre(String nombre) {
        String normalizado = nombre == null ? "" : nombre.trim().toUpperCase();
        if (!normalizado.matches(PATRON_NOMBRE)) {
            throw new BusinessRuleException("El nombre de la tanda debe tener solo letras (de 1 a 5)");
        }
        return normalizado;
    }

    /** El nombre es único entre las tandas no cerradas; una letra de una tanda cerrada se
     *  puede volver a usar. */
    static void validarNombreLibre(List<TandaCalculada> tandas, String nombre, Long idTandaExcluida) {
        boolean ocupado = tandas.stream()
            .filter(calculada -> calculada.estado() != EstadoTanda.CERRADA)
            .filter(calculada -> !calculada.tanda().getId().equals(idTandaExcluida))
            .anyMatch(calculada -> calculada.tanda().getNombre().equals(nombre));
        if (ocupado) {
            throw new BusinessRuleException("Ya existe una tanda abierta con el nombre " + nombre);
        }
    }

    private TandaCalculada buscar(List<TandaCalculada> tandas, Long idTanda) {
        return tandas.stream()
            .filter(calculada -> calculada.tanda().getId().equals(idTanda))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("No existe la tanda con id " + idTanda));
    }

    private TandaResponse construirRespuesta(TandaCalculada calculada) {
        Tanda tanda = calculada.tanda();
        return new TandaResponse(
            tanda.getId(),
            tanda.getNombre(),
            calculada.estado(),
            calculada.posicion(),
            calculada.pedidos().size(),
            calcularUnidadesPorTipoPrenda(calculada.pedidos()),
            tanda.getFechaCreacion(),
            tanda.getCreadoPor() != null ? tanda.getCreadoPor().getNombre() : null
        );
    }

    /** Incluye todas las prendas habilitadas de los pedidos de la tanda (también Bandera). */
    private List<UnidadesPorTipoPrendaResponse> calcularUnidadesPorTipoPrenda(List<Pedido> pedidos) {
        Map<String, Integer> unidades = new LinkedHashMap<>();
        for (Pedido pedido : pedidos) {
            for (Producto producto : pedido.getProductos()) {
                String tipoPrenda = producto.getTipoPrenda() != null
                    ? producto.getTipoPrenda().getNombre()
                    : SIN_TIPO_PRENDA;
                unidades.merge(tipoPrenda, producto.getCantidadTotal(), Integer::sum);
            }
        }
        return unidades.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entrada -> new UnidadesPorTipoPrendaResponse(entrada.getKey(), entrada.getValue()))
            .toList();
    }
}
