package com.weclover.backend.dto.auth;

public record LoginResponse(
    Long id,
    String nombre,
    String email,
    String rol,
    String rolDescripcion
) {
}
