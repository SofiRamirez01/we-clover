package com.weclover.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import java.time.LocalDate;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.weclover.backend.dto.kommo.KommoContact;
import com.weclover.backend.exception.ServicioNoDisponibleException;

class KommoApiClientTest {

    private static final String LEADS = KommoDePrueba.BASE_URL + "/api/v4/leads?";

    private MockRestServiceServer servidor;
    private KommoApiClient client;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl(KommoDePrueba.BASE_URL);
        servidor = MockRestServiceServer.bindTo(builder).build();
        client = new KommoApiClient(KommoDePrueba.properties(), builder.build());
    }

    @Test
    void por_fechas_filtra_por_los_estados_importables_del_pipeline_y_por_updated_at_en_hora_argentina() {
        servidor.expect(requestTo(Matchers.startsWith(LEADS)))
            .andExpect(method(HttpMethod.GET))
            .andExpect(queryParam("with", "contacts"))
            .andExpect(queryParam("page", "1"))
            .andExpect(queryParam("limit", "11"))
            // 2026-10-01 00:00:00 y 2026-10-21 23:59:59 en America/Argentina/Buenos_Aires
            .andExpect(queryParam("filter%5Bupdated_at%5D%5Bfrom%5D", "1790823600"))
            .andExpect(queryParam("filter%5Bupdated_at%5D%5Bto%5D", "1792637999"))
            .andExpect(request -> {
                String query = request.getURI().getRawQuery();
                for (int i = 0; i < 5; i++) {
                    assertThat(query).contains("filter%5Bstatuses%5D%5B" + i + "%5D%5Bpipeline_id%5D=9516980");
                }
                for (String status : List.of("73480596", "102814047", "102807927", "103172111", "102794939")) {
                    assertThat(query).containsPattern("filter%5Bstatuses%5D%5B\\d%5D%5Bstatus_id%5D=" + status);
                }
                assertThat(query).doesNotContain("filter%5Bid%5D");
            })
            .andRespond(withSuccess(KommoDePrueba.texto("pedidoCompleto.json"), MediaType.APPLICATION_JSON));

        KommoApiClient.LeadsKommo resultado = client.listarLeads(
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 21), null, 10);

        assertThat(resultado.leads()).hasSize(3);
        assertThat(resultado.hayMas()).isFalse();
        servidor.verify();
    }

    @Test
    void por_ids_no_filtra_por_pipeline_ni_estado_ni_fecha() {
        servidor.expect(requestTo(Matchers.startsWith(LEADS)))
            .andExpect(queryParam("filter%5Bid%5D%5B%5D", "28237082", "28049126"))
            .andExpect(request -> assertThat(request.getURI().getRawQuery())
                .doesNotContain("statuses")
                .doesNotContain("updated_at%5D%5Bfrom"))
            .andRespond(withSuccess(KommoDePrueba.texto("pedidoCompleto.json"), MediaType.APPLICATION_JSON));

        assertThat(client.listarLeads(null, null, List.of(28237082L, 28049126L), 10).leads()).hasSize(3);
    }

    @Test
    void si_kommo_devuelve_mas_que_el_limite_se_corta_y_avisa_que_hay_mas() {
        servidor.expect(queryParam("limit", "3"))
            .andRespond(withSuccess(KommoDePrueba.texto("pedidoCompleto.json"), MediaType.APPLICATION_JSON));

        KommoApiClient.LeadsKommo resultado = client.listarLeads(LocalDate.of(2026, 10, 1), null, null, 2);

        assertThat(resultado.leads()).hasSize(2);
        assertThat(resultado.hayMas()).isTrue();
    }

    @Test
    void sin_hasta_no_manda_tope_de_fecha_y_un_204_es_una_lista_vacia() {
        servidor.expect(request -> assertThat(request.getURI().getRawQuery())
                .contains("filter%5Bupdated_at%5D%5Bfrom%5D=1790823600")
                .doesNotContain("%5Bto%5D"))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        KommoApiClient.LeadsKommo resultado = client.listarLeads(LocalDate.of(2026, 10, 1), null, null, 10);

        assertThat(resultado.leads()).isEmpty();
        assertThat(resultado.hayMas()).isFalse();
    }

    @Test
    void trae_los_contactos_en_una_sola_llamada() {
        String cuerpo = "{\"_embedded\":{\"contacts\":[" + KommoDePrueba.texto("contactoAndre.json") + "]}}";
        servidor.expect(requestTo(Matchers.startsWith(KommoDePrueba.BASE_URL + "/api/v4/contacts?")))
            .andExpect(queryParam("filter%5Bid%5D%5B%5D", "32426660", "1"))
            .andRespond(withSuccess(cuerpo, MediaType.APPLICATION_JSON));

        List<KommoContact> contactos = client.obtenerContactos(List.of(32426660L, 1L));

        assertThat(contactos).extracting(KommoContact::name).containsExactly("Andre");
        servidor.verify();
    }

    @Test
    void contactos_con_204_es_lista_vacia() {
        servidor.expect(requestTo(Matchers.startsWith(KommoDePrueba.BASE_URL + "/api/v4/contacts?")))
            .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(client.obtenerContactos(List.of(32426660L))).isEmpty();
    }

    @Test
    void trae_las_opciones_de_un_campo_de_los_leads() {
        servidor.expect(requestTo(KommoDePrueba.BASE_URL + "/api/v4/leads/custom_fields/693578"))
            .andRespond(withSuccess("""
                {"id":693578,"name":"Vendedor","type":"select",
                 "enums":[{"id":778684,"value":"Pia","sort":2},{"id":778686,"value":"Beba","sort":1}]}
                """, MediaType.APPLICATION_JSON));

        var campo = client.obtenerCampoDeLeads(693578L);

        assertThat(campo.enums()).extracting(opcion -> opcion.id() + "=" + opcion.value())
            .containsExactly("778684=Pia", "778686=Beba");
    }

    @Test
    void sin_contactos_para_buscar_no_llama_a_kommo() {
        assertThat(client.obtenerContactos(List.of())).isEmpty();
        servidor.verify();
    }

    @Test
    void token_rechazado_es_503_sin_mostrar_el_token() {
        servidor.expect(requestTo(Matchers.startsWith(KommoDePrueba.BASE_URL)))
            .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.listarLeads(LocalDate.of(2026, 10, 1), null, null, 10))
            .isInstanceOf(ServicioNoDisponibleException.class)
            .hasMessageContaining("401")
            .hasMessageNotContaining("token-de-prueba");
    }

    @Test
    void sin_token_no_llama_y_responde_kommo_no_configurado() {
        KommoApiClient sinToken = new KommoApiClient(KommoDePrueba.properties(""), RestClient.create());

        assertThatThrownBy(() -> sinToken.listarLeads(LocalDate.of(2026, 10, 1), null, null, 10))
            .isInstanceOf(ServicioNoDisponibleException.class)
            .hasMessageStartingWith("Kommo no configurado")
            .hasMessageContaining("KOMMO_TOKEN");
    }
}
