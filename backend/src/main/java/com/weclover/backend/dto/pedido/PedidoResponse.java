package com.weclover.backend.dto.pedido;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.weclover.backend.dto.producto.ProductoResponse;
import com.weclover.backend.entity.EstadoPedido;

public record PedidoResponse(
    Long id,
    Long idColegio,
    String nombreColegio,
    String localidadColegio,
    String provinciaColegio,
    EstadoPedido estadoActual,
    Long idRepresentanteCurso,
    String nombreRepresentanteCurso,
    String telefonoRepresentanteCurso,
    String emailRepresentanteCurso,
    String codigoInterno,
    String curso,
    int cantAlumnos,
    String observaciones,
    LocalDate fechaVenta,
    LocalDate fechaEstimadaEntrega,
    LocalDateTime fechaCreacion,
    LocalDateTime fechaActualizacion,
    Long idVendedor,
    String nombreVendedor,
    String emailVendedor,
    List<ProductoResponse> productos,
    float precioTotal,
    float pagoInicial,
    float saldo,
    float porcentajePagado
) {
}
