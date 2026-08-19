package com.weclover.backend.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.pedido.PedidoCreateRequest;
import com.weclover.backend.dto.pedido.PedidoResponse;
import com.weclover.backend.dto.producto.ProductoCreateRequest;
import com.weclover.backend.entity.Colegio;
import com.weclover.backend.entity.HistorialEstadoPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.Rol;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.mapper.PedidoMapper;
import com.weclover.backend.repository.ColegioRepository;
import com.weclover.backend.repository.PedidoRepository;
import com.weclover.backend.repository.RolRepository;
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

        // Por el momento cada pedido crea su propio Colegio; la reutilización de colegios
        // existentes queda pendiente (ver doc/pantallas-pendientes.md).
        Colegio colegio = colegioRepository.save(Colegio.builder()
            .nombre(request.colegioNombre())
            .localidad(request.colegioLocalidad())
            .provincia(request.colegioProvincia())
            .build());

        Usuario representanteCurso = obtenerOCrearRepresentante(request);

        Pedido pedido = Pedido.builder()
            .colegio(colegio)
            .representanteCurso(representanteCurso)
            .creadoPor(vendedor)
            .estadoActual(request.estado())
            .codigoInterno(request.codigoInterno())
            .curso(request.curso())
            .cantAlumnos(request.cantAlumnos())
            .observaciones(request.observaciones())
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
            Producto producto = Producto.builder()
                .pedido(pedido)
                .tipoPrenda(productoRequest.tipoPrenda())
                .cantidadTotal(productoRequest.cantidadTotal())
                .costo(productoRequest.costo())
                .observaciones(productoRequest.observaciones())
                .imagenDisenoUrl(productoRequest.imagenDisenoUrl())
                .build();
            pedido.getProductos().add(producto);
        }

        Pedido guardado = pedidoRepository.save(pedido);
        return construirRespuesta(guardado, precioTotal);
    }

    /**
     * El representante de curso todavía no tiene alta ni login propios (ver doc/pantallas-pendientes.md):
     * se reutiliza por email si ya existe, o se crea como ROLE_CLIENTE con una contraseña aleatoria
     * que nadie conoce (no hay flujo de invitación/recuperación implementado aún).
     */
    private Usuario obtenerOCrearRepresentante(PedidoCreateRequest request) {
        return usuarioRepository.findByEmail(request.representanteEmail())
            .orElseGet(() -> {
                Rol rolCliente = rolRepository.findByNombre(ROL_CLIENTE)
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "No existe el rol " + ROL_CLIENTE + " necesario para dar de alta al representante"));

                return usuarioRepository.save(Usuario.builder()
                    .rol(rolCliente)
                    .nombre(request.representanteNombre())
                    .email(request.representanteEmail())
                    .telefono(request.representanteTelefono())
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

    private PedidoResponse construirRespuesta(Pedido pedido, float precioTotal) {
        PedidoResponse base = pedidoMapper.toResponse(pedido);
        float saldo = precioTotal - base.pagoInicial();
        float porcentajePagado = precioTotal > 0 ? (base.pagoInicial() / precioTotal) * 100 : 0f;

        return new PedidoResponse(
            base.id(),
            base.idColegio(),
            base.nombreColegio(),
            base.estadoActual(),
            base.idRepresentanteCurso(),
            base.nombreRepresentanteCurso(),
            base.codigoInterno(),
            base.curso(),
            base.cantAlumnos(),
            base.observaciones(),
            base.fechaCreacion(),
            base.fechaActualizacion(),
            base.idVendedor(),
            base.nombreVendedor(),
            base.productos(),
            precioTotal,
            base.pagoInicial(),
            saldo,
            porcentajePagado
        );
    }
}
