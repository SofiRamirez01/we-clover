package com.weclover.backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.weclover.backend.dto.patroncorte.PatronCorteResponse;
import com.weclover.backend.dto.tipoprenda.TipoPrendaResponse;
import com.weclover.backend.entity.PatronCorte;
import com.weclover.backend.entity.TipoPrenda;

/**
 * `colores` se ignora acá: cada PatronCorteColorResponse necesita el resumen de la Pieza
 * asignada a cada pin (PiezaResumenResponse), que requiere ir a buscar su PiezaTalle base — no
 * es una simple copia de campos, así que PatronCorteService lo arma a mano (ver construirResponse).
 */
@Mapper(componentModel = "spring")
public interface PatronCorteMapper {

    @Mapping(target = "colores", ignore = true)
    @Mapping(target = "idGrupoTalle", ignore = true)
    @Mapping(target = "nombreGrupoTalle", ignore = true)
    PatronCorteResponse toResponse(PatronCorte patronCorte);

    TipoPrendaResponse toTipoPrendaResponse(TipoPrenda tipoPrenda);
}
