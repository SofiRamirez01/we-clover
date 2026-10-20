package com.weclover.backend.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.weclover.backend.dto.corte.optimizacion.CrearTrabajoCorteDto;
import com.weclover.backend.dto.corte.optimizacion.TrabajoCorteServicioDto;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.exception.ServicioNoDisponibleException;

/**
 * Cliente HTTP hacia el optimizador de cortes (services/pieza-geometria, /corte/trabajos). El
 * servicio corre los trabajos de a uno en memoria: crear devuelve el id enseguida y el avance y
 * el resultado se consultan después.
 *
 * Errores: si no responde (o falla adentro) -> ServicioNoDisponibleException (503); si rechaza la
 * solicitud (422) -> BusinessRuleException con su motivo; si el trabajo no existe -> 404.
 */
@Component
public class CorteOptimizadorClient {

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final RestClient restClient;

    @Autowired
    public CorteOptimizadorClient(
            ObjectMapper objectMapper,
            @Value("${app.corte-optimizador.base-url}") String baseUrl,
            @Value("${app.corte-optimizador.timeout-segundos:30}") long timeoutSegundos) {
        this(objectMapper, baseUrl, RestClient.builder().requestFactory(fabrica(timeoutSegundos)));
    }

    /** Para tests: con un builder propio (ej. ligado a un MockRestServiceServer). */
    CorteOptimizadorClient(ObjectMapper objectMapper, String baseUrl, RestClient.Builder builder) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    private static SimpleClientHttpRequestFactory fabrica(long timeoutSegundos) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        fabrica.setReadTimeout(Duration.ofSeconds(timeoutSegundos));
        return fabrica;
    }

    public TrabajoCorteServicioDto crearTrabajo(CrearTrabajoCorteDto pedido) {
        return ejecutar(() -> restClient.post()
            .uri("/corte/trabajos")
            .contentType(MediaType.APPLICATION_JSON)
            .body(pedido)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                throw new BusinessRuleException("El optimizador de cortes rechazó la solicitud: "
                    + extraerDetalle(response.getBody().readAllBytes()));
            })
            .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                throw new ServicioNoDisponibleException("El optimizador de cortes falló al recibir el trabajo");
            })
            .body(TrabajoCorteServicioDto.class));
    }

    public TrabajoCorteServicioDto consultarTrabajo(String idTrabajo) {
        return ejecutar(() -> restClient.get()
            .uri("/corte/trabajos/{id}", idTrabajo)
            .retrieve()
            .onStatus(status -> status.value() == 404, (request, response) -> {
                throw new ResourceNotFoundException("No existe el trabajo de corte " + idTrabajo + " (o ya venció)");
            })
            .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                throw new ServicioNoDisponibleException("El optimizador de cortes falló al consultar el trabajo");
            })
            .body(TrabajoCorteServicioDto.class));
    }

    private <T> T ejecutar(java.util.function.Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (ResourceAccessException error) {
            throw new ServicioNoDisponibleException(
                "El optimizador de cortes no responde (" + baseUrl + "). Verificá que el servicio esté levantado.");
        } catch (RestClientException error) {
            throw new ServicioNoDisponibleException("No se pudo comunicar con el optimizador de cortes: " + error.getMessage());
        }
    }

    private String extraerDetalle(byte[] cuerpo) {
        try {
            JsonNode detalle = objectMapper.readTree(cuerpo).path("detail");
            if (detalle.isTextual()) {
                return detalle.asText();
            }
            return detalle.isMissingNode() ? "sin detalle" : detalle.toString();
        } catch (Exception error) {
            return "sin detalle";
        }
    }
}
