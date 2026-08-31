package com.weclover.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.weclover.backend.dto.proveedor.ArticuloProveedorResponse;
import com.weclover.backend.entity.ArticuloProveedor;

@Mapper(componentModel = "spring")
public interface ArticuloProveedorMapper {

    @Mapping(source = "proveedor.id", target = "idProveedor")
    @Mapping(source = "proveedor.nombre", target = "nombreProveedor")
    @Mapping(source = "paletaColor.id", target = "idPaletaColor")
    @Mapping(source = "paletaColor.nombre", target = "nombreColor")
    @Mapping(source = "paletaColor.hex", target = "hexColor")
    @Mapping(source = "paletaColor.tipoTela.codigo", target = "tipoTela")
    ArticuloProveedorResponse toResponse(ArticuloProveedor articulo);
}
