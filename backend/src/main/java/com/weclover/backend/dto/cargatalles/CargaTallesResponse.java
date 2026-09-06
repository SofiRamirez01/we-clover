package com.weclover.backend.dto.cargatalles;

import java.util.List;

import com.weclover.backend.entity.EstadoCargaTalles;

/** Respuesta completa (pública e interna comparten forma) — datos del pedido, resumen por
 *  Producto, y todos los alumnos con sus combos ya cargados. */
public record CargaTallesResponse(
    Long idPedido,
    String nombreColegio,
    String curso,
    int cantAlumnosPedido,
    EstadoCargaTalles estado,
    String token,
    List<ProductoPedidoResumenResponse> productos,
    List<AlumnoResponse> alumnos,
    /** Solo los grupos que realmente usa algún Producto de este pedido — para no mostrar la
     *  tabla de talles de una prenda que ni está en el pedido. */
    List<GrupoTallaResponse> tablasTalle
) {
}
