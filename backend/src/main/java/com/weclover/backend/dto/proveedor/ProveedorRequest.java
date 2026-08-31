package com.weclover.backend.dto.proveedor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProveedorRequest(

    @NotBlank(message = "El CUIT es obligatorio")
    @Pattern(regexp = "^\\d{11}$", message = "El CUIT debe tener 11 dígitos, sin guiones ni espacios")
    String cuit,

    @NotBlank(message = "El nombre del proveedor es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
    String nombre
) {
}
