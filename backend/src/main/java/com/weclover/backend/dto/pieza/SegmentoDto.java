package com.weclover.backend.dto.pieza;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Un tramo del contorno de una Pieza, tal cual lo arma el editor de segmentos del frontend y
 * lo espera el servicio de geometría (services/pieza-geometria). El campo "tipo" viaja como
 * propiedad propia de cada subtipo (no envuelve el objeto), por eso EXISTING_PROPERTY.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "tipo", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SegmentoRectaDto.class, name = "RECTA"),
    @JsonSubTypes.Type(value = SegmentoArcoDto.class, name = "ARCO"),
    @JsonSubTypes.Type(value = SegmentoCirculoDto.class, name = "CIRCULO"),
})
public sealed interface SegmentoDto permits SegmentoRectaDto, SegmentoArcoDto, SegmentoCirculoDto {

    String tipo();
}
