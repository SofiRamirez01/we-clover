package com.weclover.backend.dto.paletacolores;

import com.weclover.backend.entity.TipoTela;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaletaColorCreateRequest(

    @NotBlank(message = "El nombre del color es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    String nombre,

    @NotBlank(message = "El color hexadecimal es obligatorio")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El color debe tener formato hexadecimal (ej: #025939)")
    String hex,

    @NotNull(message = "Debe indicar a qué tela (o cierre) corresponde este color")
    TipoTela tipoTela
) {
}
