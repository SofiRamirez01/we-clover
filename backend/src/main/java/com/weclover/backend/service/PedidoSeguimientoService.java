package com.weclover.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.pedido.NotaPedidoResponse;
import com.weclover.backend.dto.pedido.UbicacionPedidoResponse;
import com.weclover.backend.entity.HistorialUbicacionPedido;
import com.weclover.backend.entity.NotaPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.repository.HistorialUbicacionPedidoRepository;
import com.weclover.backend.repository.NotaPedidoRepository;
import com.weclover.backend.repository.PedidoRepository;
import com.weclover.backend.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Seguimiento interno de un pedido durante la producción: notas libres (append-only) y
 * ubicación física actual. Reemplaza las columnas sueltas del Excel de priorización. Nada de
 * esto viaja en PedidoResponse: es información interna y todos los métodos validan rol.
 */
@Service
@RequiredArgsConstructor
public class PedidoSeguimientoService {

    private static final Set<String> ROLES_NOTAS = Set.of("ROLE_ADMINISTRATIVO", "ROLE_PLANTA");

    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotaPedidoRepository notaPedidoRepository;
    private final HistorialUbicacionPedidoRepository historialUbicacionPedidoRepository;
    private final AutorizacionService autorizacionService;

    /** Más nueva primero. */
    @Transactional(readOnly = true)
    public List<NotaPedidoResponse> listarNotas(Long idPedido, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_NOTAS);
        obtenerPedido(idPedido);

        return notaPedidoRepository.findByPedidoIdOrderByFechaDescIdDesc(idPedido).stream()
            .map(PedidoSeguimientoService::aRespuesta)
            .toList();
    }

    @Transactional
    public NotaPedidoResponse agregarNota(Long idPedido, String texto, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_NOTAS);
        Pedido pedido = obtenerPedido(idPedido);

        NotaPedido nota = notaPedidoRepository.save(NotaPedido.builder()
            .pedido(pedido)
            .texto(texto.trim())
            .autor(obtenerActor(idUsuarioActor))
            .fecha(LocalDateTime.now())
            .build());
        return aRespuesta(nota);
    }

    /** Solo ROLE_ADMINISTRATIVO. Si la ubicación no cambia no se registra nada. */
    @Transactional
    public UbicacionPedidoResponse cambiarUbicacion(Long idPedido, String ubicacion, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);
        Pedido pedido = obtenerPedido(idPedido);

        String nueva = ubicacion == null || ubicacion.isBlank() ? null : ubicacion.trim();
        String anterior = pedido.getUbicacionActual();
        if (!Objects.equals(anterior, nueva)) {
            historialUbicacionPedidoRepository.save(HistorialUbicacionPedido.builder()
                .pedido(pedido)
                .ubicacionAnterior(anterior)
                .ubicacionNueva(nueva)
                .usuario(obtenerActor(idUsuarioActor))
                .fecha(LocalDateTime.now())
                .build());
            pedido.setUbicacionActual(nueva);
            pedidoRepository.save(pedido);
        }
        return new UbicacionPedidoResponse(pedido.getId(), pedido.getUbicacionActual());
    }

    static NotaPedidoResponse aRespuesta(NotaPedido nota) {
        return new NotaPedidoResponse(nota.getId(), nota.getTexto(), nota.getAutor().getNombre(), nota.getFecha());
    }

    private Pedido obtenerPedido(Long idPedido) {
        return pedidoRepository.findById(idPedido)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el pedido con id " + idPedido));
    }

    private Usuario obtenerActor(Long idUsuarioActor) {
        return usuarioRepository.findById(idUsuarioActor)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el usuario que realiza la acción"));
    }
}
