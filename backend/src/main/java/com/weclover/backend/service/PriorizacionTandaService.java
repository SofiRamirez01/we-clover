package com.weclover.backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.tanda.EstadoTanda;
import com.weclover.backend.dto.tanda.MovimientoTandaRequest;
import com.weclover.backend.dto.tanda.PriorizacionRequest;
import com.weclover.backend.dto.tanda.PriorizacionResponse;
import com.weclover.backend.dto.tanda.TandaNuevaRequest;
import com.weclover.backend.dto.tanda.TandaRefRequest;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.HistorialTandaPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.SesionPriorizacion;
import com.weclover.backend.entity.Tanda;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.repository.HistorialTandaPedidoRepository;
import com.weclover.backend.repository.PedidoRepository;
import com.weclover.backend.repository.SesionPriorizacionRepository;
import com.weclover.backend.repository.TandaRepository;
import com.weclover.backend.repository.UsuarioRepository;
import com.weclover.backend.service.EstadoPedidoService.RequisitosListo;
import com.weclover.backend.service.TandaService.TandaCalculada;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Asignación de pedidos a tandas y su trazabilidad. Es el único lugar que escribe Pedido.tanda:
 * cada cambio deja un HistorialTandaPedido con el contexto del pedido en ese momento.
 *
 * Una sesión de priorización (aplicar) es atómica: primero se valida TODO contra el estado de
 * las tandas al abrir la sesión, y recién después se escribe — una regla incumplida en
 * cualquier punto no deja nada guardado (además de la transacción, que cubre fallas técnicas).
 */
@Service
@RequiredArgsConstructor
public class PriorizacionTandaService {

    static final String MOTIVO_CANCELACION = "Salida automática: el pedido fue cancelado";

    /** A, B… Z y recién después AA, AB… (como las columnas de Excel), no alfabético puro. */
    private static final Comparator<String> ORDEN_NOMBRES =
        Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder());

    private final TandaService tandaService;
    private final EstadoPedidoService estadoPedidoService;
    private final AutorizacionService autorizacionService;
    private final TandaRepository tandaRepository;
    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final SesionPriorizacionRepository sesionPriorizacionRepository;
    private final HistorialTandaPedidoRepository historialTandaPedidoRepository;
    private final ObjectMapper objectMapper;

    /** Movimiento ya validado y resuelto contra entidades. destino null = queda sin tanda. */
    private record MovimientoValidado(Pedido pedido, Tanda destino, String motivo) {
    }

    private record TandaEnOrden(Long idTanda, String nombre) {
    }

    @Transactional
    public PriorizacionResponse aplicar(PriorizacionRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);
        Usuario actor = usuarioRepository.findById(idUsuarioActor)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el usuario que realiza la priorización"));

        List<TandaNuevaRequest> tandasNuevas = request.tandasNuevas() != null ? request.tandasNuevas() : List.of();
        List<MovimientoTandaRequest> movimientos = request.movimientos() != null ? request.movimientos() : List.of();

        // ---------- 1. Validación completa, sin escribir nada ----------

        // Estado de las tandas al abrir la sesión: el congelamiento se evalúa contra esta
        // foto, no contra lo que la propia sesión va cambiando.
        List<TandaCalculada> tandasPrevias = tandaService.calcularTandas();
        Map<Long, TandaCalculada> previasPorId = new LinkedHashMap<>();
        tandasPrevias.forEach(calculada -> previasPorId.put(calculada.tanda().getId(), calculada));
        List<TandaCalculada> abiertasPrevias = tandasPrevias.stream()
            .filter(calculada -> calculada.estado() != EstadoTanda.CERRADA)
            .toList();

        Map<String, Tanda> nuevasPorIdTemporal = validarTandasNuevas(tandasNuevas, tandasPrevias, actor);
        List<MovimientoValidado> movimientosValidados = validarMovimientos(movimientos, previasPorId, nuevasPorIdTemporal);
        List<Tanda> ordenFinal = resolverOrdenFinal(request.ordenTandas(), abiertasPrevias, nuevasPorIdTemporal);

        boolean cambiaElOrden = !nuevasPorIdTemporal.isEmpty()
            || !ordenFinal.equals(abiertasPrevias.stream().map(TandaCalculada::tanda).toList());
        String nota = textoONull(request.nota());
        if (movimientosValidados.isEmpty() && !cambiaElOrden && nota == null) {
            throw new BusinessRuleException("La priorización no tiene ningún cambio para guardar");
        }

        // ---------- 2. Escritura ----------

        List<TandaEnOrden> ordenAnterior = abiertasPrevias.stream()
            .map(calculada -> new TandaEnOrden(calculada.tanda().getId(), calculada.tanda().getNombre()))
            .toList();

        // Las tandas reabiertas más adelante (un pedido que sale de ENTREGADO) quedan delante
        // de la cola actual: las abiertas se numeran a continuación de la última cerrada.
        int base = tandasPrevias.stream()
            .filter(calculada -> calculada.estado() == EstadoTanda.CERRADA)
            .mapToInt(calculada -> calculada.tanda().getOrden())
            .max()
            .orElse(0);
        for (int i = 0; i < ordenFinal.size(); i++) {
            ordenFinal.get(i).setOrden(base + i + 1);
        }
        tandaRepository.saveAll(ordenFinal);

        SesionPriorizacion sesion = SesionPriorizacion.builder()
            .fecha(LocalDateTime.now())
            .usuario(actor)
            .nota(nota)
            .build();
        if (cambiaElOrden) {
            List<TandaEnOrden> ordenNuevo = ordenFinal.stream()
                .map(tanda -> new TandaEnOrden(tanda.getId(), tanda.getNombre()))
                .toList();
            sesion.setOrdenAnteriorJson(objectMapper.writeValueAsString(ordenAnterior));
            sesion.setOrdenNuevoJson(objectMapper.writeValueAsString(ordenNuevo));
        }
        sesion = sesionPriorizacionRepository.save(sesion);

        if (!movimientosValidados.isEmpty()) {
            Map<Long, Integer> prioridadesAutomaticas = estadoPedidoService.calcularPrioridadesAutomaticas();
            for (MovimientoValidado movimiento : movimientosValidados) {
                registrarCambio(movimiento.pedido(), movimiento.destino(), sesion, actor,
                    movimiento.motivo(), prioridadesAutomaticas);
                pedidoRepository.save(movimiento.pedido());
            }
        }

        return new PriorizacionResponse(sesion.getId(), tandaService.listar(false, idUsuarioActor));
    }

    /**
     * Un pedido CANCELADO no se produce: sale de su tanda sin pasar por las reglas de
     * congelamiento ni exigir motivo. Lo llama PedidoService antes de cambiar el estado, dentro
     * de su misma transacción (el snapshot conserva el estado previo a la cancelación). No
     * guarda el Pedido: lo hace quien llama.
     */
    @Transactional
    public void sacarPorCancelacion(Pedido pedido, Usuario actor) {
        if (pedido.getTanda() == null) {
            return;
        }
        registrarCambio(pedido, null, null, actor, MOTIVO_CANCELACION,
            estadoPedidoService.calcularPrioridadesAutomaticas());
    }

    // ---------- Validaciones ----------

    private Map<String, Tanda> validarTandasNuevas(
            List<TandaNuevaRequest> tandasNuevas, List<TandaCalculada> tandasPrevias, Usuario actor) {
        Map<String, Tanda> nuevasPorIdTemporal = new LinkedHashMap<>();
        Set<String> nombresNuevos = new HashSet<>();
        for (TandaNuevaRequest nueva : tandasNuevas) {
            String nombre = TandaService.normalizarNombre(nueva.nombre());
            TandaService.validarNombreLibre(tandasPrevias, nombre, null);
            if (!nombresNuevos.add(nombre)) {
                throw new BusinessRuleException("Hay más de una tanda nueva con el nombre " + nombre);
            }
            Tanda tanda = Tanda.builder().nombre(nombre).creadoPor(actor).build();
            if (nuevasPorIdTemporal.put(nueva.idTemporal(), tanda) != null) {
                throw new BusinessRuleException("Identificador temporal de tanda repetido: " + nueva.idTemporal());
            }
        }
        return nuevasPorIdTemporal;
    }

    private List<MovimientoValidado> validarMovimientos(
            List<MovimientoTandaRequest> movimientos,
            Map<Long, TandaCalculada> previasPorId,
            Map<String, Tanda> nuevasPorIdTemporal) {
        List<MovimientoValidado> validados = new ArrayList<>();
        Set<Long> idsPedidos = new HashSet<>();

        for (MovimientoTandaRequest movimiento : movimientos) {
            if (!idsPedidos.add(movimiento.idPedido())) {
                throw new BusinessRuleException("El pedido con id " + movimiento.idPedido() + " aparece más de una vez");
            }
            Pedido pedido = pedidoRepository.findById(movimiento.idPedido())
                .orElseThrow(() -> new ResourceNotFoundException("No existe el pedido con id " + movimiento.idPedido()));
            String codigo = "#" + pedido.getCodigoInterno();

            Tanda origen = pedido.getTanda();
            Long idOrigen = origen != null ? origen.getId() : null;
            if (!Objects.equals(idOrigen, movimiento.idTandaOrigenEsperada())) {
                throw new BusinessRuleException("El pedido " + codigo
                    + " cambió de tanda mientras se priorizaba. Volvé a abrir la priorización para ver el estado actual");
            }

            Tanda destino = resolverDestino(movimiento.destino(), previasPorId, nuevasPorIdTemporal, codigo);
            if (destino != null && destino == origen) {
                throw new BusinessRuleException("El pedido " + codigo + " ya está en la tanda " + destino.getNombre());
            }
            if (origen == null && destino == null) {
                throw new BusinessRuleException("El pedido " + codigo + " ya está sin tanda");
            }

            String motivo = textoONull(movimiento.motivo());
            if (origen != null) {
                validarSalida(pedido, previasPorId.get(origen.getId()), motivo, codigo);
            }
            if (destino != null) {
                validarEntrada(pedido, destino, previasPorId, codigo);
            }
            validados.add(new MovimientoValidado(pedido, destino, motivo));
        }
        return validados;
    }

    private Tanda resolverDestino(
            TandaRefRequest destino, Map<Long, TandaCalculada> previasPorId,
            Map<String, Tanda> nuevasPorIdTemporal, String codigo) {
        if (destino == null || (destino.id() == null && destino.idTemporal() == null)) {
            return null;
        }
        return resolverReferencia(destino, previasPorId, nuevasPorIdTemporal, "el pedido " + codigo);
    }

    private Tanda resolverReferencia(
            TandaRefRequest ref, Map<Long, TandaCalculada> previasPorId,
            Map<String, Tanda> nuevasPorIdTemporal, String contexto) {
        if (ref.id() != null && ref.idTemporal() != null) {
            throw new BusinessRuleException("Referencia de tanda ambigua en " + contexto);
        }
        if (ref.id() != null) {
            TandaCalculada calculada = previasPorId.get(ref.id());
            if (calculada == null) {
                throw new ResourceNotFoundException("No existe la tanda con id " + ref.id());
            }
            return calculada.tanda();
        }
        Tanda nueva = nuevasPorIdTemporal.get(ref.idTemporal());
        if (nueva == null) {
            throw new BusinessRuleException("Referencia a una tanda nueva inexistente en " + contexto);
        }
        return nueva;
    }

    /** Sacar un pedido de una tanda en la que ya estaba: motivo obligatorio, nunca de una
     *  CERRADA, y de una EN_PRODUCCION solo si ese pedido todavía no arrancó. */
    private void validarSalida(Pedido pedido, TandaCalculada origen, String motivo, String codigo) {
        String nombre = origen.tanda().getNombre();
        if (origen.estado() == EstadoTanda.CERRADA) {
            throw new BusinessRuleException("La tanda " + nombre + " está cerrada y no se puede modificar");
        }
        if (origen.estado() == EstadoTanda.EN_PRODUCCION && TandaEstadoCalculador.tieneEtapaCompletada(pedido)) {
            throw new BusinessRuleException("El pedido " + codigo + " ya tiene etapas completadas: no se puede sacar de la tanda "
                + nombre + ", que está en producción");
        }
        if (motivo == null) {
            throw new BusinessRuleException("Debe indicar el motivo para sacar el pedido " + codigo + " de la tanda " + nombre);
        }
    }

    /** Una tanda creada en esta misma sesión acepta cualquier pedido activo (también los que
     *  ya tienen etapas completadas); una existente solo si está PLANIFICADA. */
    private void validarEntrada(Pedido pedido, Tanda destino, Map<Long, TandaCalculada> previasPorId, String codigo) {
        if (pedido.getEstadoActual() == EstadoPedido.ENTREGADO || pedido.getEstadoActual() == EstadoPedido.CANCELADO) {
            throw new BusinessRuleException("El pedido " + codigo + " está " + pedido.getEstadoActual()
                + " y no puede entrar a una tanda");
        }
        if (destino.getId() == null) {
            return;
        }
        EstadoTanda estadoDestino = previasPorId.get(destino.getId()).estado();
        if (estadoDestino == EstadoTanda.CERRADA) {
            throw new BusinessRuleException("La tanda " + destino.getNombre() + " está cerrada y no se puede modificar");
        }
        if (estadoDestino == EstadoTanda.EN_PRODUCCION) {
            throw new BusinessRuleException("La tanda " + destino.getNombre()
                + " ya está en producción: no se le pueden agregar pedidos");
        }
    }

    /**
     * Cola final: todas las tandas no cerradas más las nuevas. Si el cliente manda el orden,
     * tiene que nombrar a cada una exactamente una vez; si no, se conserva el actual y cada
     * tanda nueva entra en su lugar alfabético.
     */
    private List<Tanda> resolverOrdenFinal(
            List<TandaRefRequest> ordenTandas, List<TandaCalculada> abiertasPrevias, Map<String, Tanda> nuevasPorIdTemporal) {
        List<Tanda> esperadas = new ArrayList<>(abiertasPrevias.stream().map(TandaCalculada::tanda).toList());

        if (ordenTandas == null) {
            nuevasPorIdTemporal.values().stream()
                .sorted(Comparator.comparing(Tanda::getNombre, ORDEN_NOMBRES))
                .forEach(nueva -> {
                    int indice = 0;
                    while (indice < esperadas.size()
                            && ORDEN_NOMBRES.compare(esperadas.get(indice).getNombre(), nueva.getNombre()) < 0) {
                        indice++;
                    }
                    esperadas.add(indice, nueva);
                });
            return esperadas;
        }

        esperadas.addAll(nuevasPorIdTemporal.values());
        Map<Long, TandaCalculada> abiertasPorId = new LinkedHashMap<>();
        abiertasPrevias.forEach(calculada -> abiertasPorId.put(calculada.tanda().getId(), calculada));

        List<Tanda> orden = new ArrayList<>();
        for (TandaRefRequest ref : ordenTandas) {
            if (ref.id() != null && ref.idTemporal() == null && !abiertasPorId.containsKey(ref.id())) {
                throw new BusinessRuleException("El orden incluye una tanda inexistente o cerrada (id " + ref.id() + ")");
            }
            Tanda tanda = resolverReferencia(ref, abiertasPorId, nuevasPorIdTemporal, "el orden de tandas");
            // Por identidad: Tanda compara por id y las nuevas todavía no tienen.
            if (orden.stream().anyMatch(yaIncluida -> yaIncluida == tanda)) {
                throw new BusinessRuleException("La tanda " + tanda.getNombre() + " aparece más de una vez en el orden");
            }
            orden.add(tanda);
        }
        if (orden.size() != esperadas.size()) {
            throw new BusinessRuleException(
                "El orden de tandas está desactualizado: tiene que incluir todas las tandas abiertas. "
                    + "Volvé a abrir la priorización");
        }
        return orden;
    }

    // ---------- Escritura del evento ----------

    /** Cambia Pedido.tanda y deja el evento con el snapshot del pedido ANTES del cambio. */
    private void registrarCambio(
            Pedido pedido, Tanda destino, SesionPriorizacion sesion, Usuario actor, String motivo,
            Map<Long, Integer> prioridadesAutomaticas) {
        Tanda origen = pedido.getTanda();
        RequisitosListo requisitos = estadoPedidoService.evaluarRequisitosListo(pedido);

        historialTandaPedidoRepository.save(HistorialTandaPedido.builder()
            .pedido(pedido)
            .sesion(sesion)
            .idTandaAnterior(origen != null ? origen.getId() : null)
            .nombreTandaAnterior(origen != null ? origen.getNombre() : null)
            .idTandaNueva(destino != null ? destino.getId() : null)
            .nombreTandaNueva(destino != null ? destino.getNombre() : null)
            .usuario(actor)
            .fecha(LocalDateTime.now())
            .motivo(motivo)
            .snapshotPorcentajePagado(estadoPedidoService.calcularPorcentajePagado(pedido))
            .snapshotPrioridadAutomatica(prioridadesAutomaticas.get(pedido.getId()))
            .snapshotEstadoPedido(pedido.getEstadoActual())
            .snapshotDisenoCompleto(requisitos.disenoCompleto())
            .snapshotTallesCompletos(requisitos.tallesCompletos())
            .snapshotPagoSuficiente(requisitos.pagoSuficiente())
            .build());

        pedido.setTanda(destino);
    }

    private static String textoONull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
