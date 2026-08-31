package com.weclover.backend.dto.proveedor;

public record ProveedorResponse(
    Long id,
    String cuit,
    String nombre,
    boolean activo
) {
}
