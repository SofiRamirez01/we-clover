package com.weclover.backend.dto.pedido;

import java.time.LocalDate;
import java.util.List;

import com.weclover.backend.dto.producto.ProductoCreateRequest;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.ResponsableCurso;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record PedidoCreateRequest(

    @NotBlank(message = "El nombre del colegio es obligatorio")
    @Size(max = 150, message = "El nombre del colegio no puede superar los 150 caracteres")
    String colegioNombre,

    @NotBlank(message = "La localidad del colegio es obligatoria")
    @Size(max = 100, message = "La localidad no puede superar los 100 caracteres")
    String colegioLocalidad,

    @Size(max = 100, message = "La provincia no puede superar los 100 caracteres")
    String colegioProvincia,

    @Size(max = 50, message = "El nivel no puede superar los 50 caracteres")
    String colegioNivel,

    @NotBlank(message = "El nombre del representante de curso es obligatorio")
    @Size(max = 150, message = "El nombre del representante no puede superar los 150 caracteres")
    String representanteNombre,

    @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
    String representanteTelefono,

    @NotBlank(message = "El email del representante de curso es obligatorio")
    @Email(message = "El email del representante no tiene un formato válido")
    String representanteEmail,

    @NotNull(message = "Debe indicar el usuario vendedor que gestiona el pedido")
    Long idVendedor,

    @NotBlank(message = "El código interno es obligatorio")
    @Pattern(regexp = "^\\d{4}-\\d{2,}$", message = "El Nº de ficha debe tener el formato AAAA-NN, con NN de al menos 2 dígitos (ej: 2026-01, o 2026-100 al superar los 99 pedidos del año)")
    String codigoInterno,

    @NotBlank(message = "El curso es obligatorio")
    @Size(max = 100, message = "El curso no puede superar los 100 caracteres")
    String curso,

    @Positive(message = "La cantidad de alumnos debe ser mayor a cero")
    int cantAlumnos,

    @Size(max = 500, message = "Las observaciones no pueden superar los 500 caracteres")
    String observaciones,

    @NotNull(message = "Debe indicar el estado inicial del pedido")
    EstadoPedido estado,

    @NotNull(message = "Debe indicar la fecha de venta")
    LocalDate fechaVenta,

    @NotNull(message = "Debe indicar la fecha estimada de entrega")
    LocalDate fechaEstimadaEntrega,

    @NotEmpty(message = "Debe cargar al menos una prenda")
    @Valid
    List<ProductoCreateRequest> productos,

    @PositiveOrZero(message = "El pago inicial no puede ser negativo")
    float pagoInicial,

    ResponsableCurso responsableCurso,

    /** Boolean (no boolean primitivo): así Jackson no revienta con 400 cuando el campo no
     *  viaja en el JSON (ej. clientes que no lo conocen todavía) — se interpreta como false. */
    Boolean contratoFirmado,

    @Positive(message = "La cantidad de cuotas debe ser mayor a cero")
    Integer cantidadCuotas
) {
}
