package com.weclover.backend.mapper;

import org.mapstruct.Mapper;

import com.weclover.backend.dto.proveedor.ProveedorResponse;
import com.weclover.backend.entity.Proveedor;

@Mapper(componentModel = "spring")
public interface ProveedorMapper {

    ProveedorResponse toResponse(Proveedor proveedor);
}
