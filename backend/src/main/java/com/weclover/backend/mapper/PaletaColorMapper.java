package com.weclover.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.weclover.backend.dto.paletacolores.PaletaColorResponse;
import com.weclover.backend.entity.PaletaColores;

@Mapper(componentModel = "spring")
public interface PaletaColorMapper {

    @Mapping(source = "tipoTela.codigo", target = "tipoTela")
    PaletaColorResponse toResponse(PaletaColores paletaColores);
}
