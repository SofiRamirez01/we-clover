package com.weclover.backend.dto.cargatalles;

import java.time.LocalDateTime;
import java.util.List;

import com.weclover.backend.entity.EstadoCargaTalles;

/** Respuesta completa (pública e interna comparten forma) — datos del pedido, resumen por
 *  Producto, y todos los alumnos con sus combos ya cargados. */
public record CargaTallesResponse(
    Long idPedido,
    String codigoInterno,
    String nombreColegio,
    String localidadColegio,
    String curso,
    int cantAlumnosPedido,
    EstadoCargaTalles estado,
    String token,
    /** Null si nunca se cerró. Cuándo se cerró y quién (Vendedor/Administrativo desde Ficha
     *  Técnica, o el propio representante con "Finalizar") no viaja acá — para eso está
     *  `estado`; esto es solo para mostrarle la fecha al representante en el link público. */
    LocalDateTime fechaCierre,
    List<ProductoPedidoResumenResponse> productos,
    List<AlumnoResponse> alumnos,
    /** Solo los grupos que realmente usa algún Producto de este pedido — para no mostrar la
     *  tabla de talles de una prenda que ni está en el pedido. */
    List<GrupoTallaResponse> tablasTalle
) {
}
