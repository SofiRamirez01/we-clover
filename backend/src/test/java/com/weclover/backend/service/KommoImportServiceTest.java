package com.weclover.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.weclover.backend.dto.kommo.KommoContact;
import com.weclover.backend.dto.kommo.KommoLead;
import com.weclover.backend.dto.kommo.KommoListado;
import com.weclover.backend.dto.kommo.KommoPipeline;
import com.weclover.backend.dto.pedido.ImportacionKommoResponse;
import com.weclover.backend.dto.pedido.LeadKommoImportacion;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.SolicitudInvalidaException;

@ExtendWith(MockitoExtension.class)
class KommoImportServiceTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 10, 1);
    private static final List<Long> IDS = List.of(28237082L, 28541382L, 28152210L, 28049126L);

    @Mock
    private KommoApiClient kommoApiClient;
    @Mock
    private PedidoImportRowService pedidoImportRowService;

    private KommoImportService service;

    @BeforeEach
    void preparar() {
        service = new KommoImportService(kommoApiClient, new KommoLeadMapper(KommoDePrueba.properties()), pedidoImportRowService);
    }

    private static List<KommoLead> leadsDelListado() {
        return KommoDePrueba.leer("pedidoCompleto.json", KommoListado.class).leads();
    }

    private static PedidoImportRowService.FilaValidada validadaConVendedor(String nombre) {
        Usuario vendedor = Usuario.builder().nombre(nombre).build();
        return new PedidoImportRowService.FilaValidada("n5", "pinamar", "Andre", EstadoPedido.SENADO, vendedor,
            LocalDate.of(2026, 6, 30), LocalDate.of(2026, 12, 30), Map.of(), null, 97000f, 2328000f,
            true, 24, "2027-241", null, null);
    }

    /** Los 3 leads del listado + MAYRA (sin Ficha); solo hay contacto para Andre. */
    private void kommoDevuelveLosCuatroLeads() {
        List<KommoLead> leads = new ArrayList<>(leadsDelListado());
        leads.add(KommoDePrueba.leer("leadIncompleto.json", KommoLead.class));
        when(kommoApiClient.listarLeads(null, null, IDS, 10)).thenReturn(new KommoApiClient.LeadsKommo(leads, true));
        KommoContact andre = KommoDePrueba.leer("contactoAndre.json", KommoContact.class);
        when(kommoApiClient.obtenerContactos(argThat(ids -> ids.containsAll(List.of(32426660L, 32751502L, 32319298L)))))
            .thenReturn(List.of(andre));
    }

    @Test
    void con_ids_y_fechas_a_la_vez_es_400() {
        assertThatThrownBy(() -> service.importar(DESDE, null, List.of(1L), null, true))
            .isInstanceOf(SolicitudInvalidaException.class);
        verifyNoInteractions(kommoApiClient);
    }

    @Test
    void sin_ids_ni_desde_es_400() {
        assertThatThrownBy(() -> service.importar(null, LocalDate.of(2026, 10, 21), null, null, true))
            .isInstanceOf(SolicitudInvalidaException.class)
            .hasMessageContaining("desde");
        verifyNoInteractions(kommoApiClient);
    }

    @Test
    void limite_por_encima_del_tope_es_400_tambien_al_importar() {
        assertThatThrownBy(() -> service.importar(DESDE, null, null, 51, false))
            .isInstanceOf(SolicitudInvalidaException.class)
            .hasMessageContaining("50");
        verifyNoInteractions(kommoApiClient, pedidoImportRowService);
    }

    @Test
    void hasta_anterior_a_desde_es_400() {
        assertThatThrownBy(() -> service.importar(DESDE, DESDE.minusDays(1), null, null, true))
            .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void limite_por_defecto_es_10() {
        when(kommoApiClient.listarLeads(DESDE, null, null, 10)).thenReturn(new KommoApiClient.LeadsKommo(List.of(), false));

        ImportacionKommoResponse respuesta = service.importar(DESDE, null, null, null, true);

        assertThat(respuesta.leadsTraidos()).isZero();
        assertThat(respuesta.leads()).isEmpty();
    }

    @Test
    void simula_cada_lead_descarta_los_sin_ficha_y_explica_los_salteados() {
        kommoDevuelveLosCuatroLeads();
        when(pedidoImportRowService.validarFila(any())).thenAnswer(invocacion -> {
            FilaExcelPedido fila = invocacion.getArgument(0);
            if (fila.filaExcel() == 28237082) return validadaConVendedor("Pia Gómez");
            throw new BusinessRuleException("Falta el Contacto principal");
        });

        ImportacionKommoResponse respuesta = service.importar(null, null, IDS, null, true);

        assertThat(respuesta.simulacion()).isTrue();
        assertThat(respuesta.leadsTraidos()).isEqualTo(4);
        assertThat(respuesta.sinFicha()).isEqualTo(1);
        assertThat(respuesta.hayMasLeads()).isTrue();
        assertThat(respuesta.importados()).isEqualTo(1);
        assertThat(respuesta.salteados()).isEqualTo(2);
        assertThat(respuesta.leads()).extracting(LeadKommoImportacion::idLead)
            .containsExactly(28237082L, 28541382L, 28152210L);

        LeadKommoImportacion leadAndre = respuesta.leads().get(0);
        assertThat(leadAndre.colegio()).isEqualTo("n5");
        assertThat(leadAndre.vendedorKommo()).isEqualTo("Pia");
        assertThat(leadAndre.vendedorAsignado()).isEqualTo("Pia Gómez");
        assertThat(leadAndre.estado()).isEqualTo(EstadoPedido.SENADO);
        assertThat(leadAndre.cantidades()).isEqualTo(new LeadKommoImportacion.CantidadesPrenda(0, 24, 0, 0, true));
        assertThat(leadAndre.fichaKommo()).isEqualTo("241-27");
        assertThat(leadAndre.codigoInternoPropuesto()).isEqualTo("2027-241");
        assertThat(leadAndre.idPedido()).isNull();
        assertThat(leadAndre.motivoSalteo()).isNull();

        LeadKommoImportacion leadRomi = respuesta.leads().get(1);
        assertThat(leadRomi.codigoInternoPropuesto()).isEqualTo("2027-264");
        assertThat(leadRomi.motivoSalteo()).isEqualTo("Falta el Contacto principal");
        assertThat(leadRomi.vendedorAsignado()).isNull();
        verify(pedidoImportRowService, never()).importarFila(any());
    }

    @Test
    void importar_de_verdad_guarda_cada_lead_y_reporta_los_mismos_motivos() {
        kommoDevuelveLosCuatroLeads();
        Pedido creado = Pedido.builder().id(99L).codigoInterno("2027-241")
            .creadoPor(Usuario.builder().nombre("Pia Gómez").build()).build();
        when(pedidoImportRowService.importarFila(any())).thenAnswer(invocacion -> {
            FilaExcelPedido fila = invocacion.getArgument(0);
            if (fila.filaExcel() == 28237082) return creado;
            throw new BusinessRuleException("Falta el Contacto principal");
        });

        ImportacionKommoResponse respuesta = service.importar(null, null, IDS, null, false);

        assertThat(respuesta.simulacion()).isFalse();
        assertThat(respuesta.sinFicha()).isEqualTo(1);
        assertThat(respuesta.importados()).isEqualTo(1);
        assertThat(respuesta.salteados()).isEqualTo(2);
        LeadKommoImportacion leadAndre = respuesta.leads().get(0);
        assertThat(leadAndre.idPedido()).isEqualTo(99L);
        assertThat(leadAndre.codigoInternoPropuesto()).isEqualTo("2027-241");
        assertThat(leadAndre.vendedorAsignado()).isEqualTo("Pia Gómez");
        assertThat(respuesta.leads().get(1).motivoSalteo()).isEqualTo("Falta el Contacto principal");
        assertThat(respuesta.leads().get(1).idPedido()).isNull();
        verify(pedidoImportRowService, never()).validarFila(any());
    }

    @Test
    void un_lead_en_un_estado_no_importable_se_saltea_con_el_nombre_del_pipeline_y_del_estado() {
        KommoLead original = leadsDelListado().get(0);
        KommoLead entregado = new KommoLead(original.id(), original.name(), original.price(), 102794947L,
            original.pipelineId(), original.updatedAt(), original.customFieldsValues(), original.embebidos());
        when(kommoApiClient.listarLeads(null, null, List.of(original.id()), 10))
            .thenReturn(new KommoApiClient.LeadsKommo(List.of(entregado), false));
        when(kommoApiClient.obtenerContactos(List.of())).thenReturn(List.of());
        when(kommoApiClient.listarPipelines()).thenReturn(List.of(new KommoPipeline(9516980L, "Administracion",
            new KommoPipeline.Embebidos(List.of(new KommoPipeline.Estado(102794947L, "Entregado"))))));

        ImportacionKommoResponse respuesta = service.importar(null, null, List.of(original.id()), null, false);

        LeadKommoImportacion lead = respuesta.leads().get(0);
        assertThat(lead.estado()).isNull();
        assertThat(lead.motivoSalteo())
            .isEqualTo("estado no importable: pipeline 'Administracion', estado 'Entregado'");
        assertThat(respuesta.salteados()).isEqualTo(1);
        verifyNoInteractions(pedidoImportRowService);
        verify(kommoApiClient).verificarConfiguracion();
    }
}
