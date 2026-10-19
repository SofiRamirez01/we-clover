package com.weclover.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.weclover.backend.dto.producto.ProductoInsumoSecundarioResponse;
import com.weclover.backend.entity.ProductoInsumoSecundario;

@Mapper(componentModel = "spring")
public interface ProductoInsumoSecundarioMapper {

    @Mapping(source = "tipoTela.id", target = "idTipoTela")
    @Mapping(source = "tipoTela.codigo", target = "tipoTela")
    @Mapping(source = "tipoTela.nombre", target = "nombreTipoTela")
    @Mapping(source = "tipoTela.esPorPeso", target = "esPorPeso")
    @Mapping(source = "color.id", target = "idPaletaColor")
    @Mapping(source = "color.nombre", target = "nombreColor")
    @Mapping(source = "color.hex", target = "hexColor")
    @Mapping(source = "tipoInsumo.id", target = "idTipoInsumo")
    @Mapping(source = "tipoInsumo.codigo", target = "codigoTipoInsumo")
    @Mapping(source = "tipoInsumo.nombre", target = "nombreTipoInsumo")
    @Mapping(target = "cantidadEditadaManualmente",
        expression = "java(Boolean.TRUE.equals(insumo.getCantidadEditadaManualmente()))")
    ProductoInsumoSecundarioResponse toResponse(ProductoInsumoSecundario insumo);
}
