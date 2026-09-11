package com.weclover.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.weclover.backend.dto.pieza.PiezaResponse;
import com.weclover.backend.entity.Pieza;

/**
 * anchoBaseCm/largoBaseCm ya no viven en Pieza (ver PiezaTalle, Requisito 4.1 Parte 3): se
 * ignoran acá y PiezaService arma el PiezaResponse final completándolos desde la fila
 * PiezaTalle esBase=true de la pieza.
 */
@Mapper(componentModel = "spring")
public interface PiezaMapper {

    @Mapping(target = "idGrupoTalle", source = "grupoTalle.id")
    @Mapping(target = "nombreGrupoTalle", source = "grupoTalle.nombre")
    @Mapping(target = "idTalleBase", source = "talleBase.id")
    @Mapping(target = "talleBase", source = "talleBase.talle")
    @Mapping(target = "anchoBaseCm", ignore = true)
    @Mapping(target = "largoBaseCm", ignore = true)
    PiezaResponse toResponse(Pieza pieza);
}
