package com.weclover.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.weclover.backend.dto.producto.ProductoResponse;
import com.weclover.backend.entity.Producto;

@Mapper(componentModel = "spring", uses = { PatronCorteMapper.class, ProductoColorMapper.class })
public interface ProductoMapper {

    @Mapping(source = "tipoPrenda.id", target = "idTipoPrenda")
    @Mapping(source = "tipoPrenda.nombre", target = "tipoPrenda")
    @Mapping(source = "patronCorte.id", target = "idPatronCorte")
    @Mapping(source = "patronCorte.colores", target = "patronCorteColores")
    @Mapping(source = "colorCierre.id", target = "idColorCierre")
    @Mapping(source = "colorCierre.nombre", target = "nombreColorCierre")
    @Mapping(source = "colorCierre.hex", target = "hexColorCierre")
    @Mapping(target = "subtotal", expression = "java(producto.getCantidadTotal() * producto.getCosto())")
    ProductoResponse toResponse(Producto producto);
}
