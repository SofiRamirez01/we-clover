package com.weclover.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.weclover.backend.dto.producto.ProductoColorResponse;
import com.weclover.backend.entity.ProductoColor;

@Mapper(componentModel = "spring")
public interface ProductoColorMapper {

    @Mapping(source = "patronCorteColor.id", target = "idPatronCorteColor")
    @Mapping(source = "patronCorteColor.orden", target = "ordenPatronCorteColor")
    @Mapping(source = "paletaColor.id", target = "idPaletaColor")
    @Mapping(source = "paletaColor.nombre", target = "nombreColor")
    @Mapping(source = "paletaColor.hex", target = "hexColor")
    ProductoColorResponse toResponse(ProductoColor productoColor);
}
