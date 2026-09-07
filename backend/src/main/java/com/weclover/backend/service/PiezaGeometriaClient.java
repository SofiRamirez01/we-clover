package com.weclover.backend.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.weclover.backend.dto.pieza.CalcularBaseRequestDto;
import com.weclover.backend.dto.pieza.PiezaGeometriaCalculoResponse;
import com.weclover.backend.dto.pieza.SegmentoDto;
import com.weclover.backend.exception.BusinessRuleException;

import jakarta.annotation.PostConstruct;

/**
 * Cliente HTTP hacia services/pieza-geometria (FastAPI). Solo llama a /piezas/calcular-base:
 * /piezas/escalar todavía no lo consume nadie desde el backend Java (queda listo del lado del
 * servicio Python para la próxima entrega, cuando exista PiezaTalle).
 */
@Component
public class PiezaGeometriaClient {

    @Value("${app.pieza-geometria.base-url}")
    private String baseUrl;

    private final ObjectMapper objectMapper;
    private RestClient restClient;

    public PiezaGeometriaClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void inicializar() {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public PiezaGeometriaCalculoResponse calcularBase(List<SegmentoDto> segmentos, boolean simetrica) {
        try {
            return restClient.post()
                .uri("/piezas/calcular-base")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CalcularBaseRequestDto(segmentos, simetrica))
                .retrieve()
                .onStatus(status -> status.value() == 422, (request, response) -> {
                    throw new BusinessRuleException(extraerDetalle(response.getBody().readAllBytes()));
                })
                .body(PiezaGeometriaCalculoResponse.class);
        } catch (RestClientException error) {
            throw new BusinessRuleException(
                "No se pudo calcular la geometría de la pieza: " + error.getMessage());
        }
    }

    private String extraerDetalle(byte[] cuerpo) {
        try {
            JsonNode nodo = objectMapper.readTree(cuerpo);
            return nodo.path("detail").asText("Los datos de la pieza no son geométricamente válidos");
        } catch (Exception error) {
            return "Los datos de la pieza no son geométricamente válidos";
        }
    }
}
