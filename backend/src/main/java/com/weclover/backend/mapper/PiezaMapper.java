package com.weclover.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.weclover.backend.dto.pieza.PiezaResponse;
import com.weclover.backend.entity.Pieza;

@Mapper(componentModel = "spring")
public interface PiezaMapper {

    @Mapping(target = "idGrupoTalle", source = "grupoTalle.id")
    @Mapping(target = "nombreGrupoTalle", source = "grupoTalle.nombre")
    @Mapping(target = "idTalleBase", source = "talleBase.id")
    @Mapping(target = "talleBase", source = "talleBase.talle")
    PiezaResponse toResponse(Pieza pieza);
}
