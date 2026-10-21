package com.weclover.backend.dto.kommo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Lead de Kommo (GET /api/v4/leads, con with=contacts), solo con lo que usa la importación. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KommoLead(
    Long id,
    String name,
    Long price,
    @JsonProperty("status_id") Long statusId,
    @JsonProperty("pipeline_id") Long pipelineId,
    @JsonProperty("updated_at") Long updatedAt,
    @JsonProperty("custom_fields_values") List<KommoCustomField> customFieldsValues,
    @JsonProperty("_embedded") Embebidos embebidos
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Embebidos(List<ContactoVinculado> contacts) {
    }

    /** Referencia a un contacto del lead: with=contacts solo trae el id y si es el principal. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContactoVinculado(Long id, @JsonProperty("is_main") Boolean isMain) {
    }
}
