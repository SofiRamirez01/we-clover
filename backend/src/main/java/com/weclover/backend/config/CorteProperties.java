package com.weclover.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

/**
 * Parámetros del corte (Módulo 4), en application.properties bajo `app.corte` y no en la base:
 * son de la mesa de corte, no de cada pedido. Se devuelven tal cual en la preparación del corte
 * para que el paso siguiente (el algoritmo) trabaje con los mismos valores.
 *
 * @param anchoUtilCm   ancho útil de la tela; todas se tratan como tubulares
 * @param largoMaximoCm tope de largo de una tizada (no es un objetivo a alcanzar)
 * @param margenCorteCm margen entre piezas; en la preparación no se aplica a la geometría
 * @param capasMaximas  capas que se pueden encimar en una tizada
 * @param telasPorCapa  telas por capa: cada capa es el tubo aplanado, o sea dos telas
 */
@Validated
@ConfigurationProperties("app.corte")
public record CorteProperties(
    @Positive double anchoUtilCm,
    @Positive double largoMaximoCm,
    @Positive double margenCorteCm,
    @Positive int capasMaximas,
    @Positive int telasPorCapa
) {
}
