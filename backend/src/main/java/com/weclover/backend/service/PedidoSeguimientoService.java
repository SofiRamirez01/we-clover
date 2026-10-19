package com.weclover.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.pedido.NotaPedidoResponse;
import com.weclover.backend.entity.NotaPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.repository.NotaPedidoRepository;
import com.weclover.backend.repository.PedidoRepository;
import com.weclover.backend.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Seguimiento interno de un pedido durante la producción: notas libres (append-only).
 * Reemplaza la columna de comentarios del Excel de priorización ("falta bandera", dónde está el
 * pedido, etc.). Nada de esto viaja en PedidoResponse: es información interna y todos los
 * métodos validan rol.
 */
@Service
@RequiredArgsConstructor
public class PedidoSeguimientoService {

    private static final Set<String> ROLES_NOTAS = Set.of("ROLE_ADMINISTRATIVO", "ROLE_PLANTA");

    private final PedidoRepository pedidoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotaPedidoRepository notaPedidoRepository;
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
        Usuario autor = usuarioRepository.findById(idUsuarioActor)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el usuario que realiza la acción"));

        NotaPedido nota = notaPedidoRepository.save(NotaPedido.builder()
            .pedido(pedido)
            .texto(texto.trim())
            .autor(autor)
            .fecha(LocalDateTime.now())
            .build());
        return aRespuesta(nota);
    }

    static NotaPedidoResponse aRespuesta(NotaPedido nota) {
        return new NotaPedidoResponse(nota.getId(), nota.getTexto(), nota.getAutor().getNombre(), nota.getFecha());
    }

    private Pedido obtenerPedido(Long idPedido) {
        return pedidoRepository.findById(idPedido)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el pedido con id " + idPedido));
    }
}
