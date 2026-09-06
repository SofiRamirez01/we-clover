package com.weclover.backend.dto.cargatalles;

/** Una unidad de un Producto asignada a un alumno, con su medida (si ya se cargó). */
public record ComboResponse(
    Long id,
    Long idProducto,
    String nombreTipoPrenda,
    Integer anchoCm,
    Integer largoCm,
    String talle,
    boolean personalizado,
    String observacionPersonalizado
) {
}
