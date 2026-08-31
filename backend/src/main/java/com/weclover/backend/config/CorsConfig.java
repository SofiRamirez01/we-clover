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
                // allowedOriginPatterns (no allowedOrigins) para poder cubrir cualquier IP de
                // la LAN 192.168.0.x, no solo localhost — necesario para probar desde otro
                // dispositivo en la misma red (ver doc/pantallas-pendientes.md: esto es un
                // acceso de desarrollo temporal, no una config pensada para producción).
                registry.addMapping("/api/**")
                    .allowedOriginPatterns("http://localhost:5173", "http://192.168.0.*:5173")
                    .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    .allowedHeaders("*");

                // Necesario para que el modal de gotero (ModalColoresGotero) pueda leer
                // píxeles de la imagen con canvas.getImageData(): sin CORS acá, el canvas
                // queda "tainted" al dibujar una imagen cross-origin y el navegador bloquea
                // cualquier lectura de píxeles con SecurityError.
                registry.addMapping("/uploads/**")
                    .allowedOriginPatterns("http://localhost:5173", "http://192.168.0.*:5173")
                    .allowedMethods("GET");
            }
        };
    }
}
