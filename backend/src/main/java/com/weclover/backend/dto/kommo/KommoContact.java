package com.weclover.backend.dto.kommo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Contacto de Kommo (GET /api/v4/contacts): nombre y campos (teléfono/email por field_code). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KommoContact(
    Long id,
    String name,
    @JsonProperty("custom_fields_values") List<KommoCustomField> customFieldsValues
) {
}
