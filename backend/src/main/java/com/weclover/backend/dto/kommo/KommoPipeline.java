package com.weclover.backend.dto.kommo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Pipeline de Kommo con sus estados (GET /api/v4/leads/pipelines), solo ids y nombres. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KommoPipeline(
    Long id,
    String name,
    @JsonProperty("_embedded") Embebidos embebidos
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Embebidos(List<Estado> statuses) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Estado(Long id, String name) {
    }
}
