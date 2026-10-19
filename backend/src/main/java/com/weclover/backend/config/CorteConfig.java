package com.weclover.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra los parámetros del corte (ver CorteProperties). */
@Configuration
@EnableConfigurationProperties(CorteProperties.class)
public class CorteConfig {
}
