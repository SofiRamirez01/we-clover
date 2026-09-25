package com.weclover.backend.dto.usuario;

/** Solo id+nombre — para poblar selectores (ej. empleado que completa una etapa de
 *  producción), sin exponer email/teléfono/rol como sí hace UsuarioResponse. */
public record UsuarioResumenResponse(
    Long id,
    String nombre
) {
}
