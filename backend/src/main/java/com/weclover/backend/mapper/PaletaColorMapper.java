package com.weclover.backend.mapper;

import org.mapstruct.Mapper;

import com.weclover.backend.dto.paletacolores.PaletaColorResponse;
import com.weclover.backend.entity.PaletaColores;

@Mapper(componentModel = "spring")
public interface PaletaColorMapper {

    PaletaColorResponse toResponse(PaletaColores paletaColores);
}
