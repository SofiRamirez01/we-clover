package com.weclover.backend.dto.cargatalles;

import com.weclover.backend.entity.EstadoCargaTalles;

public record LinkCargaTallesResponse(
    Long idPedido,
    String token,
    EstadoCargaTalles estado
) {
}
