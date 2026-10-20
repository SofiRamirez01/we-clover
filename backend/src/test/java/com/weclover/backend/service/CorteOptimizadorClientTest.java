package com.weclover.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;
import com.weclover.backend.dto.corte.ParametrosCorteResponse;
import com.weclover.backend.dto.corte.PreparacionCorteResponse;
import com.weclover.backend.dto.corte.ResumenCorteResponse;
import com.weclover.backend.dto.corte.optimizacion.CrearTrabajoCorteDto;
import com.weclover.backend.dto.corte.optimizacion.TrabajoCorteServicioDto;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.exception.ServicioNoDisponibleException;

class CorteOptimizadorClientTest {

    private static final String URL = "http://optimizador:8001";

    private MockRestServiceServer servidor;
    private CorteOptimizadorClient client;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        client = new CorteOptimizadorClient(JsonMapper.builder().build(), URL, builder);
    }

    private static CrearTrabajoCorteDto pedido() {
        PreparacionCorteResponse preparacion = new PreparacionCorteResponse(7L, "A",
            new ParametrosCorteResponse(90, 800, 0.5, 20, 2), List.of(), List.of(), List.of(),
            new ResumenCorteResponse(8, 0, 0, List.of(), "", List.of()));
        return new CrearTrabajoCorteDto(preparacion, Map.of("modoTiempo", "RAPIDO"));
    }

    @Test
    void crear_trabajo_manda_la_preparacion_y_los_parametros_y_devuelve_el_id() {
        servidor.expect(requestTo(URL + "/corte/trabajos"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.preparacion.idTanda").value(7))
            .andExpect(jsonPath("$.preparacion.parametros.anchoUtilCm").value(90.0))
            .andExpect(jsonPath("$.parametros.modoTiempo").value("RAPIDO"))
            .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                .body("{\"idTrabajo\":\"abc\",\"estado\":\"EN_COLA\",\"posicionEnCola\":1}"));

        TrabajoCorteServicioDto creado = client.crearTrabajo(pedido());

        assertThat(creado.idTrabajo()).isEqualTo("abc");
        assertThat(creado.estado()).isEqualTo("EN_COLA");
        assertThat(creado.posicionEnCola()).isEqualTo(1);
        servidor.verify();
    }

    @Test
    void consultar_trabajo_lee_estado_avance_y_resultado() {
        servidor.expect(requestTo(URL + "/corte/trabajos/abc"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""
                {"idTrabajo":"abc","idTanda":7,"estado":"TERMINADO","posicionEnCola":null,
                 "progreso":{"corteActual":2,"cortesTotales":2,"corridaActual":3,"semillaActual":42,
                             "generacionActual":200,"generacionesTotales":200,"mejorLargoCm":183.4,
                             "mejorEficiencia":0.62,"segundosTranscurridos":95.3},
                 "creado":1760900000.5,"iniciado":1760900001.0,"terminado":1760900096.3,"venceEn":1760903696.3,
                 "resultado":{"cortes":[{"numero":1,"largoUsadoCm":183.4}],"noResueltas":[]}}
                """, MediaType.APPLICATION_JSON));

        TrabajoCorteServicioDto trabajo = client.consultarTrabajo("abc");

        assertThat(trabajo.idTanda()).isEqualTo(7L);
        assertThat(trabajo.estado()).isEqualTo("TERMINADO");
        assertThat(trabajo.progreso().corridaActual()).isEqualTo(3);
        assertThat(trabajo.progreso().semillaActual()).isEqualTo(42L);
        assertThat(trabajo.progreso().mejorLargoCm()).isEqualTo(183.4);
        assertThat(trabajo.creado()).isEqualTo(1760900000.5);
        assertThat(trabajo.resultado().path("cortes").get(0).path("largoUsadoCm").asDouble()).isEqualTo(183.4);
    }

    @Test
    void si_el_servicio_rechaza_la_solicitud_es_una_regla_de_negocio_con_su_motivo() {
        servidor.expect(requestTo(URL + "/corte/trabajos"))
            .andRespond(withStatus(HttpStatus.valueOf(422)).contentType(MediaType.APPLICATION_JSON)
                .body("{\"detail\":\"No hay demanda para cortar en esta preparación\"}"));

        assertThatThrownBy(() -> client.crearTrabajo(pedido()))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("No hay demanda para cortar en esta preparación");
    }

    @Test
    void un_trabajo_que_no_existe_es_404() {
        servidor.expect(requestTo(URL + "/corte/trabajos/zzz")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.consultarTrabajo("zzz"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("zzz");
    }

    @Test
    void si_el_servicio_no_responde_es_servicio_no_disponible() {
        servidor.expect(requestTo(URL + "/corte/trabajos")).andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> client.crearTrabajo(pedido()))
            .isInstanceOf(ServicioNoDisponibleException.class)
            .hasMessageContaining("no responde")
            .hasMessageContaining(URL);
    }

    @Test
    void cancelar_un_trabajo_y_409_si_ya_termino() {
        servidor.expect(requestTo(URL + "/corte/trabajos/abc")).andExpect(method(HttpMethod.DELETE))
            .andRespond(withSuccess("{\"idTrabajo\":\"abc\",\"idTanda\":7,\"estado\":\"CANCELADO\"}",
                MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(URL + "/corte/trabajos/fin")).andExpect(method(HttpMethod.DELETE))
            .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThat(client.cancelarTrabajo("abc").estado()).isEqualTo("CANCELADO");
        assertThatThrownBy(() -> client.cancelarTrabajo("fin"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("ya terminó");
    }

    @Test
    void si_el_servicio_falla_adentro_es_servicio_no_disponible() {
        servidor.expect(requestTo(URL + "/corte/trabajos/abc")).andRespond(withServerError());

        assertThatThrownBy(() -> client.consultarTrabajo("abc"))
            .isInstanceOf(ServicioNoDisponibleException.class);
    }
}
