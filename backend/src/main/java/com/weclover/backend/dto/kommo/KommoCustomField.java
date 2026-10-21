package com.weclover.backend.dto.kommo;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Un campo personalizado de un lead o contacto de Kommo. El valor llega como string o como número
 * según el tipo de campo (text/select → string; numeric/date → número, aunque numeric a veces
 * viene como string), por eso se lee como Object y se normaliza con {@link Valor#comoTexto()}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KommoCustomField(
    @JsonProperty("field_id") Long fieldId,
    @JsonProperty("field_name") String fieldName,
    @JsonProperty("field_code") String fieldCode,
    @JsonProperty("field_type") String fieldType,
    List<Valor> values
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Valor(
        Object value,
        @JsonProperty("enum_id") Long enumId,
        @JsonProperty("enum_code") String enumCode
    ) {

        /** El valor como texto; los números sin notación científica ni ".0" (97000.0 → "97000"). */
        public String comoTexto() {
            if (value == null) return null;
            if (value instanceof Number numero) {
                return new BigDecimal(numero.toString()).stripTrailingZeros().toPlainString();
            }
            return value.toString();
        }
    }

    /** Texto del primer valor del campo, o null si no tiene valores. */
    public String primerValor() {
        return (values == null || values.isEmpty()) ? null : values.get(0).comoTexto();
    }
}
