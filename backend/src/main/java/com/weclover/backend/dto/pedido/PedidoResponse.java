package com.weclover.backend.dto.pedido;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.weclover.backend.dto.producto.ProductoResponse;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.ResponsableCurso;

public record PedidoResponse(
    Long id,
    Long idColegio,
    String nombreColegio,
    String localidadColegio,
    String provinciaColegio,
    String nivelColegio,
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
    /**
     * Precio del "combo" del pedido: suma de Producto.costo de cada tipo de prenda (no
     * multiplicado por cantidad — ej. Campera $120.000 + Chomba $90.000 = $210.000). No calza
     * necesariamente con precioTotal / cantAlumnos, y está bien que no calce (un pedido puede
     * tener distinta cantidad de cada prenda). Si esa suma da 0 (pedido importado de Excel sin
     * desglose real por prenda), se usa precioUnitarioReferenciaImportado como respaldo — mismo
     * criterio que precioTotal con montoReferenciaImportado.
     */
    float precioUnitario,
    float pagoInicial,
    float saldo,
    float porcentajePagado,
    ResponsableCurso responsableCurso,
    boolean contratoFirmado,
    Integer cantidadCuotas
) {
}
