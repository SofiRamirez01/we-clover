package com.weclover.backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.pedido.CambioEstadoRequest;
import com.weclover.backend.dto.pedido.HistorialCambioResponse;
import com.weclover.backend.dto.pedido.PedidoCreateRequest;
import com.weclover.backend.dto.pedido.PedidoResponse;
import com.weclover.backend.dto.pedido.PedidoUpdateRequest;
import com.weclover.backend.dto.pedido.SnapshotAsignacionTandaResponse;
import com.weclover.backend.dto.pedido.TipoEventoHistorial;
import com.weclover.backend.dto.producto.ProductoCreateRequest;
import com.weclover.backend.dto.producto.ProductoResponse;
import com.weclover.backend.entity.Colegio;
import com.weclover.backend.entity.EstadoBandera;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.HistorialEstadoPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.PatronCorte;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.Rol;
import com.weclover.backend.entity.TipoPrenda;
import com.weclover.backend.entity.TipoTela;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.mapper.PedidoMapper;
import com.weclover.backend.repository.ColegioRepository;
import com.weclover.backend.repository.HistorialEstadoPedidoRepository;
import com.weclover.backend.repository.HistorialTandaPedidoRepository;
import com.weclover.backend.repository.HistorialUbicacionPedidoRepository;
import com.weclover.backend.repository.MovimientoEstadoRepository;
import com.weclover.backend.repository.PatronCorteRepository;
import com.weclover.backend.repository.PedidoRepository;
import com.weclover.backend.repository.RolRepository;
import com.weclover.backend.repository.TipoPrendaRepository;
import com.weclover.backend.repository.TipoTelaRepository;
import com.weclover.backend.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private static final String ROL_CLIENTE = "ROLE_CLIENTE";

    /**
     * Código de tela por defecto según el nombre del tipo de prenda (ver TipoTela.codigo),
     * precargada al crear el producto pero editable después desde el selector de tela
     * dentro del modal de gotero (ver ModalColoresGotero.tsx / ProductoService.actualizarTipoTela).
     * TipoTela pasó de enum a entidad, así que acá solo guardamos el código estable y se
     * resuelve contra la base en cada alta (resolverTipoTelaPorDefecto).
     */
    private static final Map<String, String> CODIGO_TIPO_TELA_POR_DEFECTO = Map.of(
        "Buzo", "FRIZA",
        "Remera", "JERSEY",
        "Chomba", "PIQUE",
        "Campera", "FRIZA",
        "Bandera", "SPUM"
    );

    private final PedidoRepository pedidoRepository;
    private final ColegioRepository colegioRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final TipoPrendaRepository tipoPrendaRepository;
    private final PatronCorteRepository patronCorteRepository;
    private final TipoTelaRepository tipoTelaRepository;
    private final HistorialEstadoPedidoRepository historialEstadoPedidoRepository;
    private final MovimientoEstadoRepository movimientoEstadoRepository;
    private final HistorialTandaPedidoRepository historialTandaPedidoRepository;
    private final HistorialUbicacionPedidoRepository historialUbicacionPedidoRepository;
    private final PasswordEncoder passwordEncoder;
    private final PedidoMapper pedidoMapper;
    private final ProductoService productoService;
    private final CargaTallesService cargaTallesService;
    private final ProductoEtapaProduccionService productoEtapaProduccionService;
    private final EstadoPedidoService estadoPedidoService;
    private final AutorizacionService autorizacionService;
    private final PriorizacionTandaService priorizacionTandaService;

    @Transactional
    public PedidoResponse crearPedido(PedidoCreateRequest request) {
        Usuario vendedor = usuarioRepository.findById(request.idVendedor())
            .orElseThrow(() -> new ResourceNotFoundException(
                "No existe el usuario vendedor con id " + request.idVendedor()));
        if (!vendedor.isHabilitado()) {
            throw new BusinessRuleException("El usuario vendedor indicado no se encuentra habilitado");
        }

        validarEstadoManual(request.estado());

        if (pedidoRepository.existsByCodigoInterno(request.codigoInterno())) {
            throw new BusinessRuleException(
                "Ya existe un pedido con el código interno " + request.codigoInterno());
        }

        float precioTotal = calcularPrecioTotal(request.productos());
        if (request.pagoInicial() > precioTotal) {
            throw new BusinessRuleException(
                "El pago inicial no puede ser mayor al precio total del pedido");
        }

        if (request.fechaEstimadaEntrega().isBefore(request.fechaVenta())) {
            throw new BusinessRuleException(
                "La fecha estimada de entrega no puede ser anterior a la fecha de venta");
        }

        // Por el momento cada pedido crea su propio Colegio; la reutilización de colegios
        // existentes queda pendiente (ver doc/pantallas-pendientes.md).
        Colegio colegio = colegioRepository.save(Colegio.builder()
            .nombre(request.colegioNombre())
            .localidad(request.colegioLocalidad())
            .provincia(request.colegioProvincia())
            .nivel(request.colegioNivel())
            .build());

        Usuario representanteCurso = obtenerOCrearRepresentante(
            request.representanteEmail(), request.representanteNombre(), request.representanteTelefono());

        Pedido pedido = Pedido.builder()
            .colegio(colegio)
            .representanteCurso(representanteCurso)
            .creadoPor(vendedor)
            .estadoActual(request.estado())
            .codigoInterno(request.codigoInterno())
            .curso(request.curso())
            .cantAlumnos(request.cantAlumnos())
            .observaciones(request.observaciones())
            .fechaVenta(request.fechaVenta())
            .fechaEstimadaEntrega(request.fechaEstimadaEntrega())
            .pagoInicial(request.pagoInicial())
            .responsableCurso(request.responsableCurso())
            .contratoFirmado(Boolean.TRUE.equals(request.contratoFirmado()))
            .cantidadCuotas(request.cantidadCuotas())
            .build();

        HistorialEstadoPedido historialInicial = HistorialEstadoPedido.builder()
            .pedido(pedido)
            .estado(request.estado())
            .fechaCambio(LocalDateTime.now())
            .modificadoPor(vendedor)
            .observaciones("Alta de pedido")
            .build();
        pedido.getHistorial().add(historialInicial);

        for (ProductoCreateRequest productoRequest : request.productos()) {
            TipoPrenda tipoPrenda = tipoPrendaRepository.findById(productoRequest.idTipoPrenda())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "No existe el tipo de prenda con id " + productoRequest.idTipoPrenda()));
            PatronCorte patronCorte = resolverPatronCorte(productoRequest.idPatronCorte());

            Producto producto = Producto.builder()
                .pedido(pedido)
                .tipoPrenda(tipoPrenda)
                .patronCorte(patronCorte)
                .tipoTela(resolverTipoTelaPorDefecto(tipoPrenda))
                .cantidadTotal(productoRequest.cantidadTotal())
                .costo(productoRequest.costo())
                .observaciones(productoRequest.observaciones())
                .imagenDisenoUrl(productoRequest.imagenDisenoUrl())
                .build();
            if (EtapaProduccionAplicabilidad.esBandera(producto)) {
                producto.setEstadoBandera(EstadoBandera.PENDIENTE);
            }
            pedido.getProductos().add(producto);
        }

        Pedido guardado = pedidoRepository.save(pedido);
        cargaTallesService.generarLinkParaPedidoNuevo(guardado);
        guardado.getProductos().stream()
            .filter(producto -> !EtapaProduccionAplicabilidad.esBandera(producto))
            .forEach(productoEtapaProduccionService::sincronizarEtapas);
        return construirRespuesta(guardado);
    }

    @Transactional
    public PedidoResponse cambiarEstado(Long idPedido, CambioEstadoRequest request, Long idUsuarioActor) {
        Pedido pedido = pedidoRepository.findById(idPedido)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el pedido con id " + idPedido));

        aplicarCambioEstadoManual(pedido, request.estado(), request.fechaCambio(), request.observaciones(), idUsuarioActor);

        Pedido actualizado = pedidoRepository.save(pedido);
        return construirRespuesta(actualizado);
    }

    /**
     * PRESUPUESTADO, SENADO, ENTREGADO y CANCELADO los sigue seteando una persona;
     * LISTO_PARA_PRODUCCION/EN_PRODUCCION/TERMINADO los calcula el sistema (ver
     * EstadoPedidoService.recalcularEstadoPedido) — se rechaza acá cualquier intento de
     * setearlos a mano, para que nadie salte los requisitos reales (diseño completo, talles,
     * %pago) editando el estado directamente. Compartido entre cambiarEstado y
     * actualizarPedido para no duplicar esta validación (existían dos copias casi idénticas
     * de esta lógica antes de esta entrega).
     */
    private void aplicarCambioEstadoManual(
            Pedido pedido, EstadoPedido nuevoEstado, LocalDateTime fechaCambio, String observaciones, Long idUsuarioActor) {
        validarEstadoManual(nuevoEstado);

        if (nuevoEstado == EstadoPedido.CANCELADO && pedido.getEstadoActual() == EstadoPedido.ENTREGADO) {
            throw new BusinessRuleException("No se puede cancelar un pedido que ya fue entregado");
        }

        if (nuevoEstado == EstadoPedido.ENTREGADO) {
            List<String> banderasPendientes = pedido.getProductos().stream()
                .filter(EtapaProduccionAplicabilidad::esBandera)
                .filter(producto -> producto.getEstadoBandera() != EstadoBandera.RECIBIDO)
                .map(producto -> producto.getTipoPrenda().getNombre() + " #" + producto.getId())
                .toList();
            if (!banderasPendientes.isEmpty()) {
                throw new BusinessRuleException(
                    "No se puede marcar el pedido como ENTREGADO: todavía falta recibir la bandera del proveedor ("
                        + String.join(", ", banderasPendientes) + ")");
            }
        }

        if (idUsuarioActor == null) {
            throw new BusinessRuleException("No se pudo identificar al usuario que realiza el cambio de estado");
        }
        Usuario actor = usuarioRepository.findById(idUsuarioActor)
            .orElseThrow(() -> new ResourceNotFoundException(
                "No existe el usuario que realiza el cambio de estado"));

        pedido.getHistorial().add(HistorialEstadoPedido.builder()
            .pedido(pedido)
            .estado(nuevoEstado)
            .fechaCambio(fechaCambio)
            .modificadoPor(actor)
            .observaciones(observaciones)
            .build());
        // Un pedido cancelado no se produce: sale de su tanda (queda en el historial). Se hace
        // antes de cambiar el estado para que el snapshot conserve el estado previo.
        if (nuevoEstado == EstadoPedido.CANCELADO) {
            priorizacionTandaService.sacarPorCancelacion(pedido, actor);
        }

        EstadoPedido estadoPrevio = pedido.getEstadoActual();
        pedido.setEstadoActual(nuevoEstado);

        // Entrar o salir de ENTREGADO cambia el estado de producción de cada prenda (ver
        // EstadoProduccionCalculador) — se registra con el actor real, en esta misma transacción.
        if (estadoPrevio == EstadoPedido.ENTREGADO || nuevoEstado == EstadoPedido.ENTREGADO) {
            String motivo = nuevoEstado == EstadoPedido.ENTREGADO
                ? "Pedido marcado como ENTREGADO"
                : "Pedido pasó de ENTREGADO a " + nuevoEstado;
            pedido.getProductos().forEach(producto ->
                productoEtapaProduccionService.recalcularEstadoProduccion(producto, actor, motivo));
        }
    }

    /** LISTO_PARA_PRODUCCION/EN_PRODUCCION/TERMINADO son 100% automáticos (ver
     *  EstadoPedidoService) — ni siquiera se pueden indicar como estado inicial al crear un
     *  pedido a mano. */
    private void validarEstadoManual(EstadoPedido estado) {
        if (estado == EstadoPedido.LISTO_PARA_PRODUCCION
                || estado == EstadoPedido.EN_PRODUCCION
                || estado == EstadoPedido.TERMINADO) {
            throw new BusinessRuleException(
                "El estado " + estado + " lo calcula el sistema automáticamente, no se puede indicar a mano");
        }
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> listarPedidos() {
        Map<Long, Integer> prioridadesAutomaticas = estadoPedidoService.calcularPrioridadesAutomaticas();
        return pedidoRepository.findAll().stream()
            .sorted(Comparator.comparing(Pedido::getFechaCreacion).reversed())
            .map(pedido -> construirRespuesta(pedido, prioridadesAutomaticas))
            .toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponse obtenerPedido(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el pedido con id " + id));
        return construirRespuesta(pedido);
    }

    /**
     * Historial unificado del pedido — combina HistorialEstadoPedido (cambios de EstadoPedido)
     * con MovimientoEstado (cada marcado/desmarcado de etapa y cada cambio de estado de
     * producción de cualquier prenda del pedido, ver ProductoEtapaProduccionService), ordenado por fecha
     * descendente. Es el mismo "Historial de cambios" que se ve desde los 3 puntos de Base de
     * Ventas — a pedido del negocio, todo cambio de producción tiene que quedar registrado ahí.
     * También incluye los cambios de tanda (HistorialTandaPedido) y de ubicación
     * (HistorialUbicacionPedido).
     *
     * Es información interna: se rechaza a ROLE_CLIENTE (y a quien no se identifique).
     */
    @Transactional(readOnly = true)
    public List<HistorialCambioResponse> listarHistorial(Long idPedido, Long idUsuarioActor) {
        autorizacionService.verificarRolInterno(idUsuarioActor);
        if (!pedidoRepository.existsById(idPedido)) {
            throw new ResourceNotFoundException("No existe el pedido con id " + idPedido);
        }

        List<HistorialCambioResponse> eventos = new ArrayList<>();

        historialEstadoPedidoRepository.findByPedidoIdOrderByFechaCambioDesc(idPedido).forEach(h ->
            eventos.add(new HistorialCambioResponse(
                h.getId(),
                h.getFechaCambio(),
                TipoEventoHistorial.ESTADO_PEDIDO,
                h.getEstado(),
                h.getObservaciones(),
                null,
                null,
                null,
                null,
                null,
                null,
                h.getModificadoPor() != null ? h.getModificadoPor().getNombre() : null,
                h.getModificadoPor() != null ? h.getModificadoPor().getEmail() : null
            )));

        movimientoEstadoRepository.findByProducto_Pedido_IdOrderByFechaHoraDesc(idPedido).forEach(m ->
            eventos.add(new HistorialCambioResponse(
                m.getId(),
                m.getFechaHora(),
                m.getEtapa() != null ? TipoEventoHistorial.ETAPA_PRODUCCION : TipoEventoHistorial.ESTADO_PRODUCCION,
                null,
                m.getObservaciones(),
                m.getProducto().getTipoPrenda() != null ? m.getProducto().getTipoPrenda().getNombre() : null,
                m.getEtapa(),
                m.getCompletado(),
                m.getEmpleado() != null ? m.getEmpleado().getNombre() : null,
                m.getEstadoAnterior(),
                m.getEstadoNuevo(),
                m.getUsuario() != null ? m.getUsuario().getNombre() : null,
                m.getUsuario() != null ? m.getUsuario().getEmail() : null
            )));

        historialTandaPedidoRepository.findByPedidoIdOrderByFechaDesc(idPedido).forEach(t ->
            eventos.add(new HistorialCambioResponse(
                t.getId(),
                t.getFecha(),
                TipoEventoHistorial.ASIGNACION_TANDA,
                null,
                t.getMotivo(),
                null,
                null,
                null,
                null,
                null,
                null,
                t.getUsuario().getNombre(),
                t.getUsuario().getEmail(),
                t.getIdTandaAnterior(),
                t.getNombreTandaAnterior(),
                t.getIdTandaNueva(),
                t.getNombreTandaNueva(),
                t.getSesion() != null ? t.getSesion().getId() : null,
                new SnapshotAsignacionTandaResponse(
                    t.getSnapshotPorcentajePagado(),
                    t.getSnapshotPrioridadAutomatica(),
                    t.getSnapshotEstadoPedido(),
                    t.isSnapshotDisenoCompleto(),
                    t.isSnapshotTallesCompletos(),
                    t.isSnapshotPagoSuficiente()),
                null,
                null
            )));

        historialUbicacionPedidoRepository.findByPedidoIdOrderByFechaDesc(idPedido).forEach(u ->
            eventos.add(new HistorialCambioResponse(
                u.getId(),
                u.getFecha(),
                TipoEventoHistorial.UBICACION,
                null, null, null, null, null, null, null, null,
                u.getUsuario().getNombre(),
                u.getUsuario().getEmail(),
                null, null, null, null, null, null,
                u.getUbicacionAnterior(),
                u.getUbicacionNueva()
            )));

        return eventos.stream()
            .sorted(Comparator.comparing(HistorialCambioResponse::fechaCambio).reversed())
            .toList();
    }

    @Transactional
    public PedidoResponse actualizarPedido(Long id, PedidoUpdateRequest request, Long idUsuarioActor) {
        Pedido pedido = pedidoRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el pedido con id " + id));

        if (pedidoRepository.existsByCodigoInternoAndIdNot(request.codigoInterno(), id)) {
            throw new BusinessRuleException(
                "Ya existe un pedido con el código interno " + request.codigoInterno());
        }

        float precioTotal = calcularPrecioTotal(request.productos());
        if (request.pagoInicial() > precioTotal) {
            throw new BusinessRuleException(
                "El pago inicial no puede ser mayor al precio total del pedido");
        }

        if (request.fechaEstimadaEntrega().isBefore(request.fechaVenta())) {
            throw new BusinessRuleException(
                "La fecha estimada de entrega no puede ser anterior a la fecha de venta");
        }

        Colegio colegio = pedido.getColegio();
        colegio.setNombre(request.colegioNombre());
        colegio.setLocalidad(request.colegioLocalidad());
        colegio.setProvincia(request.colegioProvincia());
        colegio.setNivel(request.colegioNivel());

        Usuario representanteActual = pedido.getRepresentanteCurso();
        if (representanteActual.getEmail().equalsIgnoreCase(request.representanteEmail())) {
            representanteActual.setNombre(request.representanteNombre());
            representanteActual.setTelefono(request.representanteTelefono());
        } else {
            pedido.setRepresentanteCurso(obtenerOCrearRepresentante(
                request.representanteEmail(), request.representanteNombre(), request.representanteTelefono()));
        }

        pedido.setCodigoInterno(request.codigoInterno());
        pedido.setCurso(request.curso());
        pedido.setCantAlumnos(request.cantAlumnos());
        pedido.setObservaciones(request.observaciones());
        pedido.setFechaVenta(request.fechaVenta());
        pedido.setFechaEstimadaEntrega(request.fechaEstimadaEntrega());
        pedido.setPagoInicial(request.pagoInicial());
        pedido.setResponsableCurso(request.responsableCurso());
        pedido.setContratoFirmado(Boolean.TRUE.equals(request.contratoFirmado()));
        pedido.setCantidadCuotas(request.cantidadCuotas());

        if (request.estado() != pedido.getEstadoActual()) {
            aplicarCambioEstadoManual(
                pedido, request.estado(), LocalDateTime.now(), "Modificado desde la edición del pedido", idUsuarioActor);
        }

        // Se matchea por id en vez de recrear todo (clear()+alta de cero), que borraba en cada
        // edición del pedido la moldería/tela/imagen/estado/colores cargados aparte desde Ficha
        // Técnica (ver doc/pantallas-pendientes.md). Un producto solo se da de baja si el usuario
        // lo quita explícitamente del formulario (no viaja su id en el payload).
        Set<Long> idsEnPayload = request.productos().stream()
            .map(ProductoCreateRequest::id)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        // Baja lógica (habilitado=false + movimiento con la fecha de baja), no DELETE: se
        // conserva el historial de la prenda. Se saca de la colección en memoria para que el
        // resto de este método (y la respuesta) ya no la vea; al no haber orphanRemoval, eso no
        // dispara ningún borrado.
        List<Producto> productosQuitados = pedido.getProductos().stream()
            .filter(producto -> !idsEnPayload.contains(producto.getId()))
            .toList();
        if (!productosQuitados.isEmpty()) {
            Usuario actorBaja = idUsuarioActor != null ? usuarioRepository.findById(idUsuarioActor).orElse(null) : null;
            productosQuitados.forEach(producto -> productoEtapaProduccionService.darDeBaja(producto, actorBaja));
            pedido.getProductos().removeAll(productosQuitados);
        }

        Map<Long, Producto> productosExistentesPorId = pedido.getProductos().stream()
            .collect(Collectors.toMap(Producto::getId, producto -> producto));

        for (ProductoCreateRequest productoRequest : request.productos()) {
            TipoPrenda tipoPrenda = tipoPrendaRepository.findById(productoRequest.idTipoPrenda())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "No existe el tipo de prenda con id " + productoRequest.idTipoPrenda()));

            if (productoRequest.id() != null) {
                Producto existente = productosExistentesPorId.get(productoRequest.id());
                if (existente == null) {
                    throw new ResourceNotFoundException(
                        "El producto con id " + productoRequest.id() + " no pertenece a este pedido");
                }
                // Solo los campos editables desde este formulario. La moldería, tela, imagen,
                // estado de producción y colores se gestionan aparte desde Ficha Técnica y no
                // deben tocarse acá.
                existente.setTipoPrenda(tipoPrenda);
                existente.setCantidadTotal(productoRequest.cantidadTotal());
                existente.setCosto(productoRequest.costo());
                existente.setObservaciones(productoRequest.observaciones());
            } else {
                PatronCorte patronCorte = resolverPatronCorte(productoRequest.idPatronCorte());
                Producto nuevo = Producto.builder()
                    .pedido(pedido)
                    .tipoPrenda(tipoPrenda)
                    .patronCorte(patronCorte)
                    .tipoTela(resolverTipoTelaPorDefecto(tipoPrenda))
                    .cantidadTotal(productoRequest.cantidadTotal())
                    .costo(productoRequest.costo())
                    .observaciones(productoRequest.observaciones())
                    .imagenDisenoUrl(productoRequest.imagenDisenoUrl())
                    .build();
                if (EtapaProduccionAplicabilidad.esBandera(nuevo)) {
                    nuevo.setEstadoBandera(EstadoBandera.PENDIENTE);
                }
                pedido.getProductos().add(nuevo);
            }
        }

        Pedido actualizado = pedidoRepository.save(pedido);
        actualizado.getProductos().stream()
            .filter(producto -> !EtapaProduccionAplicabilidad.esBandera(producto))
            .forEach(productoEtapaProduccionService::sincronizarEtapas);
        estadoPedidoService.recalcularEstadoPedido(actualizado.getId());
        return construirRespuesta(actualizado);
    }

    /**
     * El representante de curso todavía no tiene alta ni login propios (ver doc/pantallas-pendientes.md):
     * se reutiliza por email si ya existe, o se crea como ROLE_CLIENTE con una contraseña aleatoria
     * que nadie conoce (no hay flujo de invitación/recuperación implementado aún).
     */
    private Usuario obtenerOCrearRepresentante(String email, String nombre, String telefono) {
        return usuarioRepository.findByEmail(email)
            .orElseGet(() -> {
                Rol rolCliente = rolRepository.findByNombre(ROL_CLIENTE)
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe el rol " + ROL_CLIENTE + " necesario para dar de alta al representante"));

                return usuarioRepository.save(Usuario.builder()
                    .rol(rolCliente)
                    .nombre(nombre)
                    .email(email)
                    .telefono(telefono)
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .habilitado(true)
                    .build());
            });
    }

    private float calcularPrecioTotal(Iterable<ProductoCreateRequest> productos) {
        float total = 0f;
        for (ProductoCreateRequest producto : productos) {
            total += producto.cantidadTotal() * producto.costo();
        }
        return total;
    }

    /**
     * La moldería (PatronCorte) ya no es obligatoria en el alta del pedido — se elige después
     * desde Ficha Técnica (ver ProductoService.actualizarPatronCorte). null si no se mandó id.
     */
    private PatronCorte resolverPatronCorte(Long idPatronCorte) {
        if (idPatronCorte == null) {
            return null;
        }
        return patronCorteRepository.findById(idPatronCorte)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el patrón de corte con id " + idPatronCorte));
    }

    /** null si tipoPrenda no tiene default (ver CODIGO_TIPO_TELA_POR_DEFECTO) o si esa fila no está en el catálogo. */
    private TipoTela resolverTipoTelaPorDefecto(TipoPrenda tipoPrenda) {
        String codigo = CODIGO_TIPO_TELA_POR_DEFECTO.get(tipoPrenda.getNombre());
        return codigo != null ? tipoTelaRepository.findByCodigo(codigo).orElse(null) : null;
    }

    private PedidoResponse construirRespuesta(Pedido pedido) {
        return construirRespuesta(pedido, estadoPedidoService.calcularPrioridadesAutomaticas());
    }

    private PedidoResponse construirRespuesta(Pedido pedido, Map<Long, Integer> prioridadesAutomaticas) {
        PedidoResponse base = pedidoMapper.toResponse(pedido);

        // PedidoMapper arma base.productos() vía ProductoMapper directo (MapStruct), que no
        // sabe completar idColorCierre/nombreColorCierre/hexColorCierre (ya no son un campo
        // directo de Producto — ver ProductoService.construirRespuesta). Se reconstruye la
        // lista acá para que el contrato de ProductoResponse sea el mismo se llegue por
        // GET /api/pedidos o por los endpoints propios de /api/productos.
        List<ProductoResponse> productos = pedido.getProductos().stream()
            .map(productoService::construirRespuesta)
            .toList();

        // precioTotal/porcentajePagado: misma fórmula que EstadoPedidoService usa para decidir
        // LISTO_PARA_PRODUCCION — extraída a ese servicio para no mantener dos copias (antes
        // este cálculo estaba solo acá, duplicado si algún otro lugar lo necesitaba).
        float precioTotal = estadoPedidoService.calcularPrecioTotalPedido(pedido);
        float porcentajePagado = estadoPedidoService.calcularPorcentajePagado(pedido);

        // Precio del "combo": suma de Producto.costo de cada tipo de prenda (no ponderado por
        // cantidad, a diferencia de precioTotal) — ver PedidoResponse.precioUnitario. Mismo
        // respaldo que precioTotal cuando el pedido importado no tiene costo real por prenda.
        float sumaCostosPorPrenda = productos.stream()
            .map(ProductoResponse::costo)
            .reduce(0f, Float::sum);
        float precioUnitario = sumaCostosPorPrenda > 0
            ? sumaCostosPorPrenda
            : (pedido.getPrecioUnitarioReferenciaImportado() != null ? pedido.getPrecioUnitarioReferenciaImportado() : 0f);

        float saldo = precioTotal - base.pagoInicial();

        return new PedidoResponse(
            base.id(),
            base.idColegio(),
            base.nombreColegio(),
            base.localidadColegio(),
            base.provinciaColegio(),
            base.nivelColegio(),
            base.estadoActual(),
            base.idRepresentanteCurso(),
            base.nombreRepresentanteCurso(),
            base.telefonoRepresentanteCurso(),
            base.emailRepresentanteCurso(),
            base.codigoInterno(),
            base.curso(),
            base.cantAlumnos(),
            base.observaciones(),
            base.fechaVenta(),
            base.fechaEstimadaEntrega(),
            base.fechaCreacion(),
            base.fechaActualizacion(),
            base.idVendedor(),
            base.nombreVendedor(),
            base.emailVendedor(),
            productos,
            precioTotal,
            precioUnitario,
            base.pagoInicial(),
            saldo,
            porcentajePagado,
            base.responsableCurso(),
            base.contratoFirmado(),
            base.cantidadCuotas(),
            prioridadesAutomaticas.get(pedido.getId())
        );
    }
}
