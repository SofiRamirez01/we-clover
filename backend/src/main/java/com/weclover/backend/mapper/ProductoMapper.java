package com.weclover.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.weclover.backend.dto.producto.ProductoResponse;
import com.weclover.backend.entity.Producto;

@Mapper(componentModel = "spring")
public interface ProductoMapper {

    @Mapping(target = "subtotal", expression = "java(producto.getCantidadTotal() * producto.getCosto())")
    ProductoResponse toResponse(Producto producto);
}
