package com.weclover.backend.dto.cargatalles;

/**
 * Una fila del pedido para el "Resumen del pedido" de la carga de talles. `tieneTalle` = false
 * (ej. Bandera) no se ofrece como combo por alumno — se muestra solo informativamente.
 * `cantidadCargada` cuenta unidades ya cargadas por los alumnos (no alumnos distintos, ver
 * AlumnoProductoTalleRepository.countByProducto) — solo tiene sentido comparar contra
 * `cantidadTotal` cuando `tieneTalle` es true. `nombreGrupoTalle` (null si `tieneTalle` es
 * false) identifica cuál de `CargaTallesResponse.tablasTalle` usar para la vista previa de
 * talle del lado del cliente, antes de guardar (ver `calcularTalleCliente.ts`).
 */
public record ProductoPedidoResumenResponse(
    Long idProducto,
    String nombreTipoPrenda,
    int cantidadTotal,
    boolean tieneTalle,
    int cantidadCargada,
    String nombreGrupoTalle
) {
}
