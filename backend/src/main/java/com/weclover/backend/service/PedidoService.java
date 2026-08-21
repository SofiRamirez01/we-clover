package com.weclover.backend.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.pedido.CambioEstadoRequest;
import com.weclover.backend.dto.pedido.HistorialEstadoPedidoResponse;
import com.weclover.backend.dto.pedido.PedidoCreateRequest;
import com.weclover.backend.dto.pedido.PedidoResponse;
import com.weclover.backend.dto.pedido.PedidoUpdateRequest;
import com.weclover.backend.dto.producto.ProductoCreateRequest;
import com.weclover.backend.dto.producto.ProductoResponse;
import com.weclover.backend.entity.Colegio;
import com.weclover.backend.entity.HistorialEstadoPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.Rol;
import com.weclover.backend.entity.TipoPrenda;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.mapper.PedidoMapper;
import com.weclover.backend.repository.ColegioRepository;
import com.weclover.backend.repository.HistorialEstadoPedidoRepository;
import com.weclover.backend.repository.PedidoRepository;
import com.weclover.backend.repository.RolRepository;
import com.weclover.backend.repository.TipoPrendaRepository;
import com.weclover.backend.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PedidoService {

    private static final String ROL_CLIENTE = "ROLE_CLIENTE";

    private final PedidoRepository pedidoRepository;
    private final ColegioRepository colegioRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final TipoPrendaRepository tipoPrendaRepository;
    private final HistorialEstadoPedidoRepository historialEstadoPedidoRepository;
    private final PasswordEncoder passwordEncoder;
    private final PedidoMapper pedidoMapper;

    @Transactional
    public PedidoResponse crearPedido(PedidoCreateRequest request) {
        Usuario vendedor = usuarioRepository.findById(request.idVendedor())
            .orElseThrow(() -> new ResourceNotFoundException(
                "No existe el usuario vendedor con id " + request.idVendedor()));
        if (!vendedor.isHabilitado()) {
            throw new BusinessRuleException("El usuario vendedor indicado no se encuentra habilitado");
        }

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

            Producto producto = Producto.builder()
                .pedido(pedido)
                .tipoPrenda(tipoPrenda)
                .cantidadTotal(productoRequest.cantidadTotal())
                .costo(productoRequest.costo())
                .observaciones(productoRequest.observaciones())
                .imagenDisenoUrl(productoRequest.imagenDisenoUrl())
                .build();
            pedido.getProductos().add(producto);
        }

        Pedido guardado = pedidoRepository.save(pedido);
        return construirRespuesta(guardado);
    }

    @Transactional
    public PedidoResponse cambiarEstado(Long idPedido, CambioEstadoRequest request, Long idUsuarioActor) {
        Pedido pedido = pedidoRepository.findById(idPedido)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el pedido con id " + idPedido));

        if (idUsuarioActor == null) {
            throw new BusinessRuleException("No se pudo identificar al usuario que realiza el cambio de estado");
        }
        Usuario actor = usuarioRepository.findById(idUsuarioActor)
            .orElseThrow(() -> new ResourceNotFoundException(
                "No existe el usuario que realiza el cambio de estado"));

        HistorialEstadoPedido historial = HistorialEstadoPedido.builder()
            .pedido(pedido)
            .estado(request.estado())
            .fechaCambio(request.fechaCambio())
            .modificadoPor(actor)
            .observaciones(request.observaciones())
            .build();
        pedido.getHistorial().add(historial);
        pedido.setEstadoActual(request.estado());

        Pedido actualizado = pedidoRepository.save(pedido);
        return construirRespuesta(actualizado);
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> listarPedidos() {
        return pedidoRepository.findAll().stream()
            .sorted(Comparator.comparing(Pedido::getFechaCreacion).reversed())
            .map(this::construirRespuesta)
            .toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponse obtenerPedido(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el pedido con id " + id));
        return construirRespuesta(pedido);
    }

    @Transactional(readOnly = true)
    public List<HistorialEstadoPedidoResponse> listarHistorial(Long idPedido) {
        if (!pedidoRepository.existsById(idPedido)) {
            throw new ResourceNotFoundException("No existe el pedido con id " + idPedido);
        }
        return historialEstadoPedidoRepository.findByPedidoIdOrderByFechaCambioDesc(idPedido).stream()
            .map(h -> new HistorialEstadoPedidoResponse(
                h.getId(),
                h.getEstado(),
                h.getFechaCambio(),
                h.getObservaciones(),
                h.getModificadoPor().getNombre(),
                h.getModificadoPor().getEmail()
            ))
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

        if (request.estado() != pedido.getEstadoActual()) {
            if (idUsuarioActor == null) {
                throw new BusinessRuleException(
                    "No se pudo identificar al usuario que realiza el cambio de estado");
            }
            Usuario actor = usuarioRepository.findById(idUsuarioActor)
                .orElseThrow(() -> new ResourceNotFoundException(
                    "No existe el usuario que realiza el cambio de estado"));

            HistorialEstadoPedido historial = HistorialEstadoPedido.builder()
                .pedido(pedido)
                .estado(request.estado())
                .fechaCambio(LocalDateTime.now())
                .modificadoPor(actor)
                .observaciones("Modificado desde la edición del pedido")
                .build();
            pedido.getHistorial().add(historial);
            pedido.setEstadoActual(request.estado());
        }

        pedido.getProductos().clear();
        for (ProductoCreateRequest productoRequest : request.productos()) {
            TipoPrenda tipoPrenda = tipoPrendaRepository.findById(productoRequest.idTipoPrenda())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "No existe el tipo de prenda con id " + productoRequest.idTipoPrenda()));

            Producto producto = Producto.builder()
                .pedido(pedido)
                .tipoPrenda(tipoPrenda)
                .cantidadTotal(productoRequest.cantidadTotal())
                .costo(productoRequest.costo())
                .observaciones(productoRequest.observaciones())
                .imagenDisenoUrl(productoRequest.imagenDisenoUrl())
                .build();
            pedido.getProductos().add(producto);
        }

        Pedido actualizado = pedidoRepository.save(pedido);
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

    private PedidoResponse construirRespuesta(Pedido pedido) {
        PedidoResponse base = pedidoMapper.toResponse(pedido);
        float precioTotal = base.productos().stream()
            .map(ProductoResponse::subtotal)
            .reduce(0f, Float::sum);
        float saldo = precioTotal - base.pagoInicial();
        float porcentajePagado = precioTotal > 0 ? (base.pagoInicial() / precioTotal) * 100 : 0f;

        return new PedidoResponse(
            base.id(),
            base.idColegio(),
            base.nombreColegio(),
            base.localidadColegio(),
            base.provinciaColegio(),
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
            base.productos(),
            precioTotal,
            base.pagoInicial(),
            saldo,
            porcentajePagado
        );
    }
}
