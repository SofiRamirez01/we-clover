package com.weclover.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.weclover.backend.dto.kommo.KommoContact;
import com.weclover.backend.dto.kommo.KommoLead;
import com.weclover.backend.dto.kommo.KommoListado;
import com.weclover.backend.dto.kommo.KommoPipeline;
import com.weclover.backend.dto.kommo.SincronizacionKommoResponse;
import com.weclover.backend.dto.pedido.ImportacionKommoResponse;
import com.weclover.backend.dto.pedido.LeadKommoImportacion;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.SincronizacionKommo;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ForbiddenException;
import com.weclover.backend.exception.PedidoYaImportadoException;
import com.weclover.backend.exception.SolicitudInvalidaException;
import com.weclover.backend.repository.SincronizacionKommoRepository;
import com.weclover.backend.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class KommoImportServiceTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 10, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 10, 21);
    private static final List<Long> IDS = List.of(28237082L, 28541382L, 28152210L, 28049126L);
    private static final Long ADMIN = 14L;

    @Mock private KommoApiClient kommoApiClient;
    @Mock private PedidoImportRowService pedidoImportRowService;
    @Mock private SincronizacionKommoRepository sincronizacionKommoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AutorizacionService autorizacionService;

    private KommoImportService service;

    @BeforeEach
    void preparar() {
        service = new KommoImportService(kommoApiClient, new KommoLeadMapper(KommoDePrueba.properties()),
            pedidoImportRowService, sincronizacionKommoRepository, usuarioRepository, autorizacionService);
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
    private void kommoDevuelveLosCuatroLeads(boolean hayMas) {
        List<KommoLead> leads = new ArrayList<>(leadsDelListado());
        leads.add(KommoDePrueba.leer("leadIncompleto.json", KommoLead.class));
        when(kommoApiClient.listarLeads(null, null, IDS, 10)).thenReturn(new KommoApiClient.LeadsKommo(leads, hayMas));
        KommoContact andre = KommoDePrueba.leer("contactoAndre.json", KommoContact.class);
        when(kommoApiClient.obtenerContactos(argThat(ids -> ids.containsAll(List.of(32426660L, 32751502L, 32319298L)))))
            .thenReturn(List.of(andre));
    }

    @Test
    void con_ids_y_fechas_a_la_vez_es_400() {
        assertThatThrownBy(() -> service.importar(DESDE, null, List.of(1L), null, true, null))
            .isInstanceOf(SolicitudInvalidaException.class);
        verifyNoInteractions(kommoApiClient);
    }

    @Test
    void sin_ids_ni_desde_es_400() {
        assertThatThrownBy(() -> service.importar(null, HASTA, null, null, true, null))
            .isInstanceOf(SolicitudInvalidaException.class)
            .hasMessageContaining("desde");
        verifyNoInteractions(kommoApiClient);
    }

    @Test
    void por_ids_el_tope_es_50_y_por_fechas_250() {
        assertThatThrownBy(() -> service.importar(null, null, List.of(1L), 51, true, null))
            .isInstanceOf(SolicitudInvalidaException.class)
            .hasMessageContaining("50");
        assertThatThrownBy(() -> service.importar(DESDE, HASTA, null, 251, true, null))
            .isInstanceOf(SolicitudInvalidaException.class)
            .hasMessageContaining("250");
        verifyNoInteractions(kommoApiClient, pedidoImportRowService);
    }

    @Test
    void hasta_anterior_a_desde_es_400() {
        assertThatThrownBy(() -> service.importar(DESDE, DESDE.minusDays(1), null, null, true, null))
            .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void por_fechas_trae_el_rango_completo_hasta_250_y_por_ids_10_por_defecto() {
        when(kommoApiClient.listarLeads(DESDE, HASTA, null, 250)).thenReturn(new KommoApiClient.LeadsKommo(List.of(), false));
        when(kommoApiClient.listarLeads(null, null, List.of(1L), 10)).thenReturn(new KommoApiClient.LeadsKommo(List.of(), false));

        assertThat(service.importar(DESDE, HASTA, null, null, true, null).maximoLeads()).isEqualTo(250);
        assertThat(service.importar(null, null, List.of(1L), null, true, null).maximoLeads()).isEqualTo(10);
    }

    @Test
    void importar_de_verdad_exige_un_usuario_administrativo() {
        doThrow(new ForbiddenException("No se pudo identificar al usuario que realiza la acción"))
            .when(autorizacionService).verificarRolAdministrativo(null);

        assertThatThrownBy(() -> service.importar(DESDE, HASTA, null, null, false, null))
            .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(kommoApiClient, pedidoImportRowService, sincronizacionKommoRepository);
    }

    @Test
    void simula_cada_lead_descarta_los_sin_ficha_y_explica_los_salteados() {
        kommoDevuelveLosCuatroLeads(true);
        when(pedidoImportRowService.validarFila(any())).thenAnswer(invocacion -> {
            FilaExcelPedido fila = invocacion.getArgument(0);
            if (fila.filaExcel() == 28237082) return validadaConVendedor("Pia Gómez");
            if (fila.filaExcel() == 28541382) throw new PedidoYaImportadoException("El lead #28541382 ya se importó como pedido 2027-264");
            throw new BusinessRuleException("Falta el Contacto principal");
        });

        ImportacionKommoResponse respuesta = service.importar(null, null, IDS, null, true, null);

        assertThat(respuesta.simulacion()).isTrue();
        assertThat(respuesta.leadsTraidos()).isEqualTo(4);
        assertThat(respuesta.sinFicha()).isEqualTo(1);
        assertThat(respuesta.hayMasLeads()).isTrue();
        assertThat(respuesta.importados()).isEqualTo(1);
        assertThat(respuesta.salteados()).isEqualTo(2);
        assertThat(respuesta.yaImportados()).isEqualTo(1);
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

        assertThat(respuesta.leads().get(1).yaImportado()).isTrue();
        LeadKommoImportacion leadZulma = respuesta.leads().get(2);
        assertThat(leadZulma.motivoSalteo()).isEqualTo("Falta el Contacto principal");
        assertThat(leadZulma.yaImportado()).isFalse();
        verify(pedidoImportRowService, never()).importarFila(any());
        verifyNoInteractions(sincronizacionKommoRepository);
    }

    @Test
    void importar_de_verdad_guarda_cada_lead_y_registra_la_sincronizacion() {
        kommoDevuelveLosCuatroLeads(false);
        Pedido creado = Pedido.builder().id(99L).codigoInterno("2027-241")
            .creadoPor(Usuario.builder().nombre("Pia Gómez").build()).build();
        when(pedidoImportRowService.importarFila(any())).thenAnswer(invocacion -> {
            FilaExcelPedido fila = invocacion.getArgument(0);
            if (fila.filaExcel() == 28237082) return creado;
            throw new BusinessRuleException("Falta el Contacto principal");
        });
        Usuario admin = Usuario.builder().id(ADMIN).nombre("Catalina Pla").build();
        when(usuarioRepository.getReferenceById(ADMIN)).thenReturn(admin);

        ImportacionKommoResponse respuesta = service.importar(null, null, IDS, null, false, ADMIN);

        assertThat(respuesta.simulacion()).isFalse();
        assertThat(respuesta.importados()).isEqualTo(1);
        assertThat(respuesta.salteados()).isEqualTo(2);
        LeadKommoImportacion leadAndre = respuesta.leads().get(0);
        assertThat(leadAndre.idPedido()).isEqualTo(99L);
        assertThat(leadAndre.vendedorAsignado()).isEqualTo("Pia Gómez");
        verify(pedidoImportRowService, never()).validarFila(any());

        ArgumentCaptor<SincronizacionKommo> registro = ArgumentCaptor.forClass(SincronizacionKommo.class);
        verify(sincronizacionKommoRepository).save(registro.capture());
        assertThat(registro.getValue().getUsuario()).isSameAs(admin);
        assertThat(registro.getValue().getIdsLeads()).isEqualTo(IDS);
        assertThat(registro.getValue().getDesde()).isNull();
        assertThat(registro.getValue().getImportados()).isEqualTo(1);
        assertThat(registro.getValue().getSalteados()).isEqualTo(2);
        assertThat(registro.getValue().getSinFicha()).isEqualTo(1);
        assertThat(registro.getValue().getFechaHora()).isNotNull();
    }

    @Test
    void si_el_rango_supera_el_maximo_no_importa_nada_ni_registra() {
        when(kommoApiClient.listarLeads(DESDE, HASTA, null, 250))
            .thenReturn(new KommoApiClient.LeadsKommo(leadsDelListado(), true));

        assertThatThrownBy(() -> service.importar(DESDE, HASTA, null, null, false, ADMIN))
            .isInstanceOf(SolicitudInvalidaException.class)
            .hasMessageContaining("250")
            .hasMessageContaining("Achicá el rango");
        verifyNoInteractions(pedidoImportRowService, sincronizacionKommoRepository);
        verify(kommoApiClient, never()).obtenerContactos(any());
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
        when(usuarioRepository.getReferenceById(ADMIN)).thenReturn(Usuario.builder().id(ADMIN).build());

        ImportacionKommoResponse respuesta = service.importar(null, null, List.of(original.id()), null, false, ADMIN);

        LeadKommoImportacion lead = respuesta.leads().get(0);
        assertThat(lead.estado()).isNull();
        assertThat(lead.motivoSalteo())
            .isEqualTo("estado no importable: pipeline 'Administracion', estado 'Entregado'");
        assertThat(respuesta.salteados()).isEqualTo(1);
        verifyNoInteractions(pedidoImportRowService);
        verify(kommoApiClient).verificarConfiguracion();
    }

    @Test
    void la_ultima_sincronizacion_es_solo_para_administrativos() {
        Usuario admin = Usuario.builder().id(ADMIN).nombre("Catalina Pla").build();
        LocalDateTime cuando = LocalDateTime.of(2026, 10, 21, 10, 30);
        when(sincronizacionKommoRepository.findFirstByOrderByFechaHoraDescIdDesc()).thenReturn(Optional.of(
            SincronizacionKommo.builder().id(3L).fechaHora(cuando).usuario(admin).desde(DESDE).hasta(HASTA)
                .importados(5).salteados(2).sinFicha(1).build()));

        Optional<SincronizacionKommoResponse> ultima = service.ultimaSincronizacion(ADMIN);

        verify(autorizacionService).verificarRolAdministrativo(ADMIN);
        assertThat(ultima).contains(new SincronizacionKommoResponse(
            3L, cuando, ADMIN, "Catalina Pla", DESDE, HASTA, List.of(), 5, 2, 1));
    }
}
