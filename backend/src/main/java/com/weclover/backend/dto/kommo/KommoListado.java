package com.weclover.backend.dto.kommo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta de un listado de la API v4 de Kommo: los elementos vienen en _embedded, bajo una
 * clave que depende del recurso (leads, contacts, pipelines).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KommoListado(@JsonProperty("_embedded") Embebidos embebidos) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Embebidos(
        List<KommoLead> leads,
        List<KommoContact> contacts,
        List<KommoPipeline> pipelines
    ) {
    }

    public List<KommoLead> leads() {
        return embebidos == null || embebidos.leads() == null ? List.of() : embebidos.leads();
    }

    public List<KommoContact> contactos() {
        return embebidos == null || embebidos.contacts() == null ? List.of() : embebidos.contacts();
    }

    public List<KommoPipeline> pipelines() {
        return embebidos == null || embebidos.pipelines() == null ? List.of() : embebidos.pipelines();
    }
}
