package com.weclover.backend.dto.pedido;

import java.time.LocalDateTime;
import java.util.List;

import com.weclover.backend.dto.producto.ProductoResponse;
import com.weclover.backend.entity.EstadoPedido;

public record PedidoResponse(
    Long id,
    Long idColegio,
    String nombreColegio,
    EstadoPedido estadoActual,
    Long idRepresentanteCurso,
    String nombreRepresentanteCurso,
    String codigoInterno,
    String curso,
    int cantAlumnos,
    String observaciones,
    LocalDateTime fechaCreacion,
    LocalDateTime fechaActualizacion,
    Long idVendedor,
    String nombreVendedor,
    List<ProductoResponse> productos,
    float precioTotal,
    float pagoInicial,
    float saldo,
    float porcentajePagado
) {
}
