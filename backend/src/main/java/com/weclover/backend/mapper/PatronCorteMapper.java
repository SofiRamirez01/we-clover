package com.weclover.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.weclover.backend.dto.patroncorte.PatronCorteColorResponse;
import com.weclover.backend.dto.patroncorte.PatronCorteResponse;
import com.weclover.backend.entity.PatronCorte;
import com.weclover.backend.entity.PatronCorteColor;

@Mapper(componentModel = "spring")
public interface PatronCorteMapper {

    @Mapping(source = "tipoPrenda.id", target = "idTipoPrenda")
    @Mapping(source = "tipoPrenda.nombre", target = "tipoPrenda")
    PatronCorteResponse toResponse(PatronCorte patronCorte);

    PatronCorteColorResponse toColorResponse(PatronCorteColor color);
}
