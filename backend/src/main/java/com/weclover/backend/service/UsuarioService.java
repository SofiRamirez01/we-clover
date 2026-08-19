package com.weclover.backend.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.rol.RolResponse;
import com.weclover.backend.dto.usuario.UsuarioCreateRequest;
import com.weclover.backend.dto.usuario.UsuarioResponse;
import com.weclover.backend.dto.usuario.UsuarioUpdateRequest;
import com.weclover.backend.entity.Rol;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ForbiddenException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.mapper.UsuarioMapper;
import com.weclover.backend.repository.RolRepository;
import com.weclover.backend.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    /**
     * ROLE_CLIENTE se reserva para los representantes de curso que se dan de alta
     * automáticamente al crear un pedido (ver PedidoService); esta pantalla es solo
     * para usuarios corporativos.
     */
    private static final String ROL_CLIENTE = "ROLE_CLIENTE";

    /** Único rol habilitado para editar o eliminar usuarios desde esta pantalla. */
    private static final String ROL_ADMINISTRATIVO = "ROLE_ADMINISTRATIVO";

    /** Contraseña provisoria hasta que exista el flujo de invitación por mail (ver doc/pantallas-pendientes.md). */
    private static final String PASSWORD_PROVISORIA = "123";

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper usuarioMapper;

    @Transactional(readOnly = true)
    public List<RolResponse> listarRolesCorporativos() {
        return rolRepository.findAll().stream()
            .filter(rol -> !ROL_CLIENTE.equals(rol.getNombre()))
            .map(rol -> new RolResponse(rol.getId(), rol.getNombre(), rol.getDescripcion()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listarUsuariosCorporativos() {
        return usuarioRepository.findByRolNombreNotAndHabilitadoTrueOrderByNombreAsc(ROL_CLIENTE).stream()
            .map(usuarioMapper::toResponse)
            .toList();
    }

    @Transactional
    public UsuarioResponse crearUsuario(UsuarioCreateRequest request) {
        Rol rol = obtenerRolCorporativo(request.idRol());

        if (usuarioRepository.findByEmail(request.email()).isPresent()) {
            throw new BusinessRuleException(
                "Ya existe un usuario con el email " + request.email());
        }

        Usuario usuario = Usuario.builder()
            .rol(rol)
            .nombre(request.nombre())
            .email(request.email())
            .telefono(request.telefono())
            .passwordHash(passwordEncoder.encode(PASSWORD_PROVISORIA))
            .habilitado(true)
            .build();

        return usuarioMapper.toResponse(usuarioRepository.save(usuario));
    }

    @Transactional
    public UsuarioResponse actualizarUsuario(Long id, UsuarioUpdateRequest request, Long idUsuarioActor) {
        verificarActorAdministrativo(idUsuarioActor);

        Usuario usuario = usuarioRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el usuario con id " + id));

        Rol rol = obtenerRolCorporativo(request.idRol());

        usuarioRepository.findByEmail(request.email())
            .filter(existente -> !existente.getId().equals(id))
            .ifPresent(existente -> {
                throw new BusinessRuleException("Ya existe un usuario con el email " + request.email());
            });

        usuario.setNombre(request.nombre());
        usuario.setEmail(request.email());
        usuario.setTelefono(request.telefono());
        usuario.setRol(rol);

        return usuarioMapper.toResponse(usuarioRepository.save(usuario));
    }

    /**
     * "Eliminar" deshabilita al usuario en vez de borrar la fila: Usuario está referenciado
     * por Pedido (creadoPor/representanteCurso) e HistorialEstadoPedido (modificadoPor), y
     * borrarlo de verdad rompería esas relaciones y la trazabilidad exigida por el negocio.
     * Un usuario deshabilitado no puede loguearse (ver AuthService) y desaparece del listado.
     */
    @Transactional
    public void eliminarUsuario(Long id, Long idUsuarioActor) {
        verificarActorAdministrativo(idUsuarioActor);

        Usuario usuario = usuarioRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el usuario con id " + id));

        usuario.setHabilitado(false);
        usuarioRepository.save(usuario);
    }

    private Rol obtenerRolCorporativo(Long idRol) {
        Rol rol = rolRepository.findById(idRol)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el rol con id " + idRol));

        if (ROL_CLIENTE.equals(rol.getNombre())) {
            throw new BusinessRuleException(
                "No se puede asignar el rol " + ROL_CLIENTE + " desde esta pantalla; "
                    + "se genera automáticamente al crear un pedido");
        }
        return rol;
    }

    private void verificarActorAdministrativo(Long idUsuarioActor) {
        if (idUsuarioActor == null) {
            throw new ForbiddenException("No se pudo identificar al usuario que realiza la acción");
        }

        Usuario actor = usuarioRepository.findById(idUsuarioActor)
            .orElseThrow(() -> new ForbiddenException("El usuario que realiza la acción no existe"));

        if (!ROL_ADMINISTRATIVO.equals(actor.getRol().getNombre())) {
            throw new ForbiddenException(
                "Solo un usuario con rol " + ROL_ADMINISTRATIVO + " puede editar o eliminar usuarios");
        }
    }
}
