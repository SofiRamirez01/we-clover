package com.weclover.backend.dto.pedido;

/**
 * Una opción del filtro "moldería – tipo de prenda". Va separada por tipo de prenda porque un
 * mismo nombre puede repetirse entre molderías distintas (ej. "Clásica" n°1 de Buzo/Campera y
 * "Clásica" n°1 de Chomba/Remera, ver PatronCorte.numeroInterno).
 */
public record OpcionMolderiaResponse(
    Long idPatronCorte,
    Integer numeroInterno,
    String nombrePatronCorte,
    Long idTipoPrenda,
    String tipoPrenda
) {
}
