package com.weclover.backend.mapper;

import org.mapstruct.Mapper;

import com.weclover.backend.dto.patroncorte.PatronCorteColorResponse;
import com.weclover.backend.dto.patroncorte.PatronCorteResponse;
import com.weclover.backend.dto.tipoprenda.TipoPrendaResponse;
import com.weclover.backend.entity.PatronCorte;
import com.weclover.backend.entity.PatronCorteColor;
import com.weclover.backend.entity.TipoPrenda;

@Mapper(componentModel = "spring")
public interface PatronCorteMapper {

    PatronCorteResponse toResponse(PatronCorte patronCorte);

    PatronCorteColorResponse toColorResponse(PatronCorteColor color);

    TipoPrendaResponse toTipoPrendaResponse(TipoPrenda tipoPrenda);
}
