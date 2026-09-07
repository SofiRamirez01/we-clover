package com.weclover.backend.dto.tablatalle;

public record TablaTalleResponse(
    Long id,
    String talle,
    int anchoCm,
    int largoCm
) {
}
