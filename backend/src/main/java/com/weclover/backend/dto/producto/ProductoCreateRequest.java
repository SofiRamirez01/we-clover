package com.weclover.backend.dto.producto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductoCreateRequest(

    /**
     * Null para una prenda nueva. Al editar un pedido (PedidoService.actualizarPedido), si se
     * manda, identifica qué Producto existente actualizar in-place en vez de recrearlo —
     * necesario para no perder la moldería/tela/imagen/colores ya cargados desde Ficha
     * Técnica. Se ignora en el alta de pedido (crearPedido), donde todos los productos son
     * nuevos.
     */
    Long id,

    @NotNull(message = "Debe seleccionar el tipo de prenda")
    Long idTipoPrenda,

    /**
     * Ya no es obligatorio en el alta del pedido: la moldería (nombre de negocio para
     * PatronCorte) se elige después, desde Ficha Técnica (ver
     * ProductoService.actualizarPatronCorte). Se mantiene el campo/endpoint sin cambios de
     * forma para no romper al frontend que ya existe.
     */
    Long idPatronCorte,

    @Positive(message = "La cantidad debe ser mayor a cero")
    int cantidadTotal,

    @PositiveOrZero(message = "El costo no puede ser negativo")
    float costo,

    @Size(max = 500, message = "Las observaciones no pueden superar los 500 caracteres")
    String observaciones,

    @Size(max = 500, message = "La URL de la imagen no puede superar los 500 caracteres")
    String imagenDisenoUrl
) {
}
