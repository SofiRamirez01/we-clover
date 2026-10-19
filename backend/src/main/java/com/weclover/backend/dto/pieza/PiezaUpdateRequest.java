package com.weclover.backend.dto.pieza;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record PiezaUpdateRequest(
    @NotBlank String nombre,
    @NotNull Long idGrupoTalle,
    @NotNull Long idTalleBase,
    @NotEmpty @Valid List<SegmentoDto> segmentos,
    boolean simetrica,
    /** "Tela de la pieza": tipo de insumo en el que se corta (ver TipoInsumo), o null = cuerpo. */
    Long idTipoInsumo
) {
}
