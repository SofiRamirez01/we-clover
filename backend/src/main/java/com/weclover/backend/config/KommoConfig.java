package com.weclover.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Registra KommoProperties y el RestClient hacia la API de Kommo, ya con la base URL y el header
 * Authorization: Bearer. Si Kommo no está configurado el bean se crea igual (sin base URL ni
 * token) para no impedir el arranque: KommoApiClient corta antes de llamar y responde 503.
 */
@Configuration
@EnableConfigurationProperties(KommoProperties.class)
public class KommoConfig {

    @Bean
    public RestClient kommoRestClient(KommoProperties properties) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        fabrica.setReadTimeout(Duration.ofSeconds(30));

        RestClient.Builder builder = RestClient.builder().requestFactory(fabrica);
        if (properties.baseUrl() != null && !properties.baseUrl().isBlank()) {
            builder.baseUrl(properties.baseUrl());
        }
        if (properties.token() != null && !properties.token().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token());
        }
        return builder.build();
    }
}
