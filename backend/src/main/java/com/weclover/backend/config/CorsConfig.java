package com.weclover.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("http://localhost:5173")
                    .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    .allowedHeaders("*");

                // Necesario para que el modal de gotero (ModalColoresGotero) pueda leer
                // píxeles de la imagen con canvas.getImageData(): sin CORS acá, el canvas
                // queda "tainted" al dibujar una imagen cross-origin y el navegador bloquea
                // cualquier lectura de píxeles con SecurityError.
                registry.addMapping("/uploads/**")
                    .allowedOrigins("http://localhost:5173")
                    .allowedMethods("GET");
            }
        };
    }
}
