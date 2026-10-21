package com.weclover.backend.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Component;

import com.weclover.backend.config.KommoProperties;
import com.weclover.backend.dto.kommo.KommoContact;
import com.weclover.backend.dto.kommo.KommoCustomField;
import com.weclover.backend.dto.kommo.KommoLead;
import com.weclover.backend.entity.EstadoPedido;

import lombok.RequiredArgsConstructor;

/**
 * Arma, a partir de un lead de la API de Kommo y su contacto principal, el mismo
 * {@link FilaExcelPedido} que sale del Excel, para reutilizar sin cambios la validación y el
 * guardado de {@link PedidoImportRowService}. Cada campo se busca por field_id (configurado en
 * {@link KommoProperties.Campos}), nunca por posición: un campo ausente queda en null.
 */
@Component
@RequiredArgsConstructor
class KommoLeadMapper {

    static final ZoneId ZONA_ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    /** Mismo formato que el Excel, que es el que espera PedidoImportRowService.parsearFecha. */
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String CODIGO_TELEFONO = "PHONE";
    private static final String CODIGO_EMAIL = "EMAIL";

    private final KommoProperties properties;

    /** Los leads sin Ficha se descartan antes de mapear: sin Ficha no hay Nº de ficha. */
    boolean tieneFicha(KommoLead lead) {
        String ficha = valor(lead.customFieldsValues(), properties.campos().ficha());
        return ficha != null && !ficha.isBlank();
    }

    /** EstadoPedido según el status_id del lead, o null si ese estado no se importa. */
    EstadoPedido estadoImportable(KommoLead lead) {
        return lead.statusId() == null ? null : properties.estadosImportables().get(lead.statusId());
    }

    /**
     * @param contactoPrincipal contacto is_main del lead ya traído de /contacts; puede ser null
     *                          (la validación de la fila reporta entonces que falta el contacto)
     */
    FilaExcelPedido mapear(KommoLead lead, KommoContact contactoPrincipal) {
        KommoProperties.Campos campos = properties.campos();
        List<KommoCustomField> valores = lead.customFieldsValues();
        EstadoPedido estado = estadoImportable(lead);
        List<KommoCustomField> valoresContacto = contactoPrincipal == null ? null : contactoPrincipal.customFieldsValues();

        return new FilaExcelPedido(
            Math.toIntExact(lead.id()),
            valor(valores, campos.colegio()),
            valor(valores, campos.localidad()),
            valor(valores, campos.provincia()),
            valor(valores, campos.nivel()),
            contactoPrincipal == null ? null : contactoPrincipal.name(),
            valorPorCodigo(valoresContacto, CODIGO_TELEFONO),
            valorPorCodigo(valoresContacto, CODIGO_EMAIL),
            valor(valores, campos.vendedor()),
            estado == null ? null : estado.name(),
            valor(valores, campos.promo()),
            valor(valores, campos.ficha()),
            valor(valores, campos.precioUnitario()),
            lead.price() == null ? null : lead.price().toString(),
            valor(valores, campos.buzos()),
            valor(valores, campos.camperas()),
            valor(valores, campos.remeras()),
            valor(valores, campos.chombas()),
            valor(valores, campos.bandera()),
            valor(valores, campos.contrato()),
            valor(valores, campos.pique()),
            valor(valores, campos.tejido()),
            fecha(valor(valores, campos.fechaVenta())),
            fecha(valor(valores, campos.fechaEntregaPactada())),
            valor(valores, campos.cuotas()),
            valor(valores, campos.responsable()),
            List.of(),
            enumId(valores, campos.vendedor()),
            lead.id()
        );
    }

    /** Contacto marcado is_main entre los vinculados al lead (null si no tiene). */
    static Long idContactoPrincipal(KommoLead lead) {
        if (lead.embebidos() == null || lead.embebidos().contacts() == null) return null;
        return lead.embebidos().contacts().stream()
            .filter(contacto -> Boolean.TRUE.equals(contacto.isMain()))
            .map(KommoLead.ContactoVinculado::id)
            .findFirst()
            .orElse(null);
    }

    private static String valor(List<KommoCustomField> campos, Long fieldId) {
        if (campos == null || fieldId == null) return null;
        return campos.stream()
            .filter(campo -> fieldId.equals(campo.fieldId()))
            .findFirst()
            .map(KommoCustomField::primerValor)
            .orElse(null);
    }

    /** enum_id del primer valor de un campo select (null si no es select o no tiene valor). */
    private static Long enumId(List<KommoCustomField> campos, Long fieldId) {
        if (campos == null || fieldId == null) return null;
        return campos.stream()
            .filter(campo -> fieldId.equals(campo.fieldId()))
            .findFirst()
            .filter(campo -> campo.values() != null && !campo.values().isEmpty())
            .map(campo -> campo.values().get(0).enumId())
            .orElse(null);
    }

    private static String valorPorCodigo(List<KommoCustomField> campos, String fieldCode) {
        if (campos == null) return null;
        return campos.stream()
            .filter(campo -> fieldCode.equals(campo.fieldCode()))
            .findFirst()
            .map(KommoCustomField::primerValor)
            .orElse(null);
    }

    /** Timestamp Unix (segundos) → "dd.MM.yyyy" en hora de Argentina; si no es un número, tal cual. */
    private static String fecha(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) return null;
        try {
            return Instant.ofEpochSecond(Long.parseLong(timestamp.trim())).atZone(ZONA_ARGENTINA).format(FORMATO_FECHA);
        } catch (NumberFormatException e) {
            return timestamp;
        }
    }
}
