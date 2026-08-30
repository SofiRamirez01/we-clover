package com.weclover.backend.dto.producto;

/** idPatronCorte puede ser null para desasignar la moldería del producto. */
public record ActualizarPatronCorteRequest(
    Long idPatronCorte
) {
}
