package com.weclover.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.weclover.backend.dto.producto.ProductoResponse;
import com.weclover.backend.entity.Producto;

@Mapper(componentModel = "spring")
public interface ProductoMapper {

    @Mapping(source = "tipoPrenda.id", target = "idTipoPrenda")
    @Mapping(source = "tipoPrenda.nombre", target = "tipoPrenda")
    @Mapping(target = "subtotal", expression = "java(producto.getCantidadTotal() * producto.getCosto())")
    ProductoResponse toResponse(Producto producto);
}
