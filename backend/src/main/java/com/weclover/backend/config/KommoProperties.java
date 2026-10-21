package com.weclover.backend.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.weclover.backend.entity.EstadoPedido;

/**
 * Conexión con la API v4 de Kommo (CRM), bajo `app.kommo` en application-local.yml (ignorado por
 * git; ver application-local.example.yml). A propósito no se valida al arrancar: si falta el
 * archivo o la variable KOMMO_TOKEN el backend levanta igual y solo la importación desde Kommo
 * responde 503 (ver {@link #faltantes()}).
 *
 * @param baseUrl             ej. https://catalinaplas.kommo.com
 * @param token               token de larga duración; viene de la variable de entorno KOMMO_TOKEN,
 *                            nunca va en el código ni en respuestas/logs
 * @param pipelineId          pipeline del que se importan los leads ("Administracion")
 * @param estadosImportables  status_id de Kommo → EstadoPedido; un lead en cualquier otro estado
 *                            no se importa
 * @param campos              field_id de cada campo personalizado del lead que se usa
 */
@ConfigurationProperties("app.kommo")
public record KommoProperties(
    String baseUrl,
    String token,
    Long pipelineId,
    Map<Long, EstadoPedido> estadosImportables,
    Campos campos
) {

    public KommoProperties {
        estadosImportables = estadosImportables == null ? Map.of() : Map.copyOf(estadosImportables);
    }

    /** field_id de los campos personalizados del lead (se buscan por id, nunca por posición). */
    public record Campos(
        Long colegio,
        Long localidad,
        Long provincia,
        Long nivel,
        Long responsable,
        Long promo,
        Long ficha,
        Long precioUnitario,
        Long buzos,
        Long camperas,
        Long remeras,
        Long chombas,
        Long bandera,
        Long contrato,
        Long pique,
        Long tejido,
        Long fechaVenta,
        Long fechaEntregaPactada,
        Long cuotas,
        Long vendedor
    ) {
    }

    /** Nombres de las propiedades que faltan para poder llamar a Kommo (vacía = configurado). */
    public List<String> faltantes() {
        List<String> faltan = new ArrayList<>();
        if (baseUrl == null || baseUrl.isBlank()) faltan.add("base-url");
        if (token == null || token.isBlank()) faltan.add("token (KOMMO_TOKEN)");
        if (pipelineId == null) faltan.add("pipeline-id");
        if (estadosImportables.isEmpty()) faltan.add("estados-importables");
        if (campos == null) {
            faltan.add("campos");
        } else {
            for (var componente : Campos.class.getRecordComponents()) {
                try {
                    if (componente.getAccessor().invoke(campos) == null) faltan.add("campos." + componente.getName());
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return faltan;
    }
}
