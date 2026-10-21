package com.weclover.backend.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import com.weclover.backend.config.KommoProperties;
import com.weclover.backend.dto.kommo.KommoCampoPersonalizado;
import com.weclover.backend.dto.kommo.KommoContact;
import com.weclover.backend.dto.kommo.KommoLead;
import com.weclover.backend.dto.kommo.KommoListado;
import com.weclover.backend.dto.kommo.KommoPipeline;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.exception.ServicioNoDisponibleException;

/**
 * Cliente de solo lectura hacia la API v4 de Kommo (ver developers.kommo.com/reference). Las
 * llamadas son secuenciales, sin paralelismo, para no acercarse al límite de pedidos por
 * segundo de Kommo. Ningún mensaje de error incluye el token ni el cuerpo de la respuesta.
 *
 * Errores: Kommo sin configurar, sin respuesta, con credenciales rechazadas o fallando ->
 * ServicioNoDisponibleException (503). Kommo responde 204 sin cuerpo cuando un listado no tiene
 * resultados: se toma como lista vacía.
 */
@Component
public class KommoApiClient {

    /** Máximo de elementos por página que acepta la API de Kommo. */
    static final int LIMITE_PAGINA_KOMMO = 250;

    private final KommoProperties properties;
    private final RestClient restClient;

    public KommoApiClient(KommoProperties properties, @Qualifier("kommoRestClient") RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /** Leads traídos y si quedaron más en Kommo sin traer por el límite pedido. */
    public record LeadsKommo(List<KommoLead> leads, boolean hayMas) {
    }

    /** Corta con 503 si falta algo de app.kommo (se llama antes de cualquier consulta). */
    public void verificarConfiguracion() {
        List<String> faltantes = properties.faltantes();
        if (!faltantes.isEmpty()) {
            throw new ServicioNoDisponibleException("Kommo no configurado (falta: " + String.join(", ", faltantes) + ")");
        }
    }

    /**
     * Leads con sus contactos vinculados (with=contacts), ordenados por fecha de actualización.
     * Con ids se piden esos leads (filter[id][]) sin filtrar por pipeline ni estado; sin ids,
     * los del pipeline configurado en alguno de los estados importables (filter[statuses]),
     * actualizados entre desde y hasta (filter[updated_at], hasta inclusive al final del día,
     * en hora de Argentina; hasta null = sin tope).
     *
     * Se pide un lead más que el límite para saber, sin otra llamada, si quedaron más.
     */
    public LeadsKommo listarLeads(LocalDate desde, LocalDate hasta, List<Long> ids, int limite) {
        verificarConfiguracion();
        boolean porIds = ids != null && !ids.isEmpty();
        int aTraer = limite + 1;
        int tamanioPagina = Math.min(LIMITE_PAGINA_KOMMO, aTraer);
        List<KommoLead> leads = new ArrayList<>();

        for (int pagina = 1; leads.size() < aTraer; pagina++) {
            int paginaActual = pagina;
            List<KommoLead> recibidos = obtenerListado(uri -> {
                uri.path("/api/v4/leads")
                    .queryParam("with", "contacts")
                    .queryParam("page", paginaActual)
                    .queryParam("limit", tamanioPagina)
                    .queryParam("order[updated_at]", "asc");
                if (porIds) {
                    ids.forEach(id -> uri.queryParam("filter[id][]", id));
                } else {
                    agregarFiltroEstados(uri);
                    agregarFiltroFechas(uri, desde, hasta);
                }
                return uri;
            }, "los leads").leads();
            leads.addAll(recibidos);
            if (recibidos.size() < tamanioPagina) break;
        }

        boolean hayMas = leads.size() > limite;
        return new LeadsKommo(hayMas ? List.copyOf(leads.subList(0, limite)) : List.copyOf(leads), hayMas);
    }

    /** Contactos pedidos, en una sola llamada (filter[id][]). Sin ids no llama a Kommo. */
    public List<KommoContact> obtenerContactos(Collection<Long> ids) {
        verificarConfiguracion();
        if (ids == null || ids.isEmpty()) return List.of();
        if (ids.size() > LIMITE_PAGINA_KOMMO) {
            throw new IllegalArgumentException("Se pidieron " + ids.size() + " contactos, el máximo por llamada es " + LIMITE_PAGINA_KOMMO);
        }
        return obtenerListado(uri -> {
            uri.path("/api/v4/contacts").queryParam("limit", LIMITE_PAGINA_KOMMO);
            ids.forEach(id -> uri.queryParam("filter[id][]", id));
            return uri;
        }, "los contactos").contactos();
    }

    /** Pipelines con sus estados, para mostrar nombres en vez de ids. */
    public List<KommoPipeline> listarPipelines() {
        verificarConfiguracion();
        return obtenerListado(uri -> uri.path("/api/v4/leads/pipelines"), "los pipelines").pipelines();
    }

    /** Definición de un campo personalizado de los leads, con sus opciones (enums). */
    public KommoCampoPersonalizado obtenerCampoDeLeads(long fieldId) {
        verificarConfiguracion();
        KommoCampoPersonalizado campo = ejecutar(() -> restClient.get()
            .uri("/api/v4/leads/custom_fields/{id}", fieldId)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> {
                throw errorDeKommo(response.getStatusCode().value(), "el campo " + fieldId);
            })
            .body(KommoCampoPersonalizado.class), "el campo " + fieldId);
        if (campo == null) {
            throw new ServicioNoDisponibleException("Kommo no devolvió el campo " + fieldId);
        }
        return campo;
    }

    /** filter[statuses][i][pipeline_id] + [status_id] por cada estado importable. */
    private void agregarFiltroEstados(UriBuilder uri) {
        int i = 0;
        for (Map.Entry<Long, EstadoPedido> estado : properties.estadosImportables().entrySet()) {
            uri.queryParam("filter[statuses][" + i + "][pipeline_id]", properties.pipelineId());
            uri.queryParam("filter[statuses][" + i + "][status_id]", estado.getKey());
            i++;
        }
    }

    private void agregarFiltroFechas(UriBuilder uri, LocalDate desde, LocalDate hasta) {
        if (desde != null) {
            uri.queryParam("filter[updated_at][from]",
                desde.atStartOfDay(KommoLeadMapper.ZONA_ARGENTINA).toEpochSecond());
        }
        if (hasta != null) {
            uri.queryParam("filter[updated_at][to]",
                hasta.plusDays(1).atStartOfDay(KommoLeadMapper.ZONA_ARGENTINA).toEpochSecond() - 1);
        }
    }

    private KommoListado obtenerListado(Function<UriBuilder, UriBuilder> uri, String recurso) {
        KommoListado listado = ejecutar(() -> restClient.get()
            .uri(builder -> uri.apply(builder).build())
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> {
                throw errorDeKommo(response.getStatusCode().value(), recurso);
            })
            .body(KommoListado.class), recurso);
        return listado == null ? new KommoListado(null) : listado;
    }

    private static ServicioNoDisponibleException errorDeKommo(int status, String recurso) {
        if (status == 401 || status == 403) {
            return new ServicioNoDisponibleException("Kommo rechazó las credenciales (HTTP " + status + "): revisar KOMMO_TOKEN");
        }
        if (status == 429) {
            return new ServicioNoDisponibleException("Kommo limitó la cantidad de pedidos (HTTP 429): reintentar en unos segundos");
        }
        return new ServicioNoDisponibleException("Kommo respondió HTTP " + status + " al consultar " + recurso);
    }

    private <T> T ejecutar(Supplier<T> llamada, String recurso) {
        try {
            return llamada.get();
        } catch (ResourceAccessException error) {
            throw new ServicioNoDisponibleException("Kommo no responde (" + properties.baseUrl() + ") al consultar " + recurso);
        } catch (RestClientException error) {
            throw new ServicioNoDisponibleException("No se pudo leer la respuesta de Kommo al consultar " + recurso);
        }
    }
}
