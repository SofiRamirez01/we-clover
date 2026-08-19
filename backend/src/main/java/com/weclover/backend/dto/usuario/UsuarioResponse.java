package com.weclover.backend.dto.usuario;

public record UsuarioResponse(
    Long id,
    String nombre,
    String email,
    String telefono,
    Long idRol,
    String rol,
    String rolDescripcion,
    boolean habilitado
) {
}
