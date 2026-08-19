package com.weclover.backend.dto.pedido;

import java.util.List;

import com.weclover.backend.dto.producto.ProductoCreateRequest;
import com.weclover.backend.entity.EstadoPedido;

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
    @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "El Nº de ficha debe tener el formato AAAA-NN (ej: 2026-01)")
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

    @NotEmpty(message = "Debe cargar al menos una prenda")
    @Valid
    List<ProductoCreateRequest> productos,

    @PositiveOrZero(message = "El pago inicial no puede ser negativo")
    float pagoInicial
) {
}
