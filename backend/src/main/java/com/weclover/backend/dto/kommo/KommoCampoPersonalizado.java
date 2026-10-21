package com.weclover.backend.dto.kommo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Definición de un campo personalizado (GET /api/v4/leads/custom_fields/{id}): sus opciones. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KommoCampoPersonalizado(
    Long id,
    String name,
    List<Opcion> enums
) {

    /** Una opción de un campo select/multiselect: el enum_id que viaja en el valor del lead. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Opcion(Long id, String value, Integer sort) {
    }
}
