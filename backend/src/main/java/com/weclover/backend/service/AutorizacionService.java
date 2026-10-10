package com.weclover.backend.service;

import java.util.Set;

import org.springframework.stereotype.Service;

import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.ForbiddenException;
import com.weclover.backend.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Validación provisoria basada en el header X-Usuario-Id (ver doc/pantallas-pendientes.md):
 * no es autenticación real, solo una regla de negocio sobre un dato que el cliente declara.
 * Reemplazar por el rol que salga de un JWT verificado cuando exista sesión real.
 */
@Service
@RequiredArgsConstructor
public class AutorizacionService {

    private static final String ROL_ADMINISTRATIVO = "ROLE_ADMINISTRATIVO";
    private static final String ROL_CLIENTE = "ROLE_CLIENTE";

    private final UsuarioRepository usuarioRepository;

    public void verificarRolAdministrativo(Long idUsuarioActor) {
        verificarRolPermitido(idUsuarioActor, Set.of(ROL_ADMINISTRATIVO));
    }

    public void verificarRolPermitido(Long idUsuarioActor, Set<String> rolesPermitidos) {
        Usuario actor = obtenerActor(idUsuarioActor);
        if (!rolesPermitidos.contains(actor.getRol().getNombre())) {
            throw new ForbiddenException(
                "Esta acción requiere uno de estos roles: " + String.join(", ", rolesPermitidos));
        }
    }

    /** Cualquier usuario corporativo: todos los roles menos ROLE_CLIENTE (representantes de
     *  curso). Para información interna que no debe llegar al cliente. */
    public void verificarRolInterno(Long idUsuarioActor) {
        Usuario actor = obtenerActor(idUsuarioActor);
        if (ROL_CLIENTE.equals(actor.getRol().getNombre())) {
            throw new ForbiddenException("Esta acción es solo para usuarios internos");
        }
    }

    private Usuario obtenerActor(Long idUsuarioActor) {
        if (idUsuarioActor == null) {
            throw new ForbiddenException("No se pudo identificar al usuario que realiza la acción");
        }
        return usuarioRepository.findById(idUsuarioActor)
            .orElseThrow(() -> new ForbiddenException("El usuario que realiza la acción no existe"));
    }
}
