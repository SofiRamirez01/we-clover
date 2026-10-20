package com.weclover.backend.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void un_servicio_que_no_responde_es_503_con_el_mensaje() {
        ResponseEntity<Map<String, Object>> respuesta = handler.handleServicioNoDisponible(
            new ServicioNoDisponibleException("El optimizador de cortes no responde"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(respuesta.getBody()).containsEntry("status", 503)
            .containsEntry("mensaje", "El optimizador de cortes no responde");
    }

    @Test
    void sin_demanda_es_409_como_las_demas_reglas_de_negocio() {
        assertThat(handler.handleBusinessRule(new BusinessRuleException("no tiene demanda para cortar")).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }
}
