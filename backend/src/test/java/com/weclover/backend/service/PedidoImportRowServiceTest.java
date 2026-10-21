package com.weclover.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.OrigenPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.Rol;
import com.weclover.backend.entity.TipoPrenda;
import com.weclover.backend.entity.TipoTela;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.repository.ColegioRepository;
import com.weclover.backend.repository.PedidoRepository;
import com.weclover.backend.repository.RolRepository;
import com.weclover.backend.repository.TipoPrendaRepository;
import com.weclover.backend.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class PedidoImportRowServiceTest {

    private static final long ENUM_PIA = 778684L;
    private static final long LEAD_ANDRE = 28237082L;

    @Mock private PedidoRepository pedidoRepository;
    @Mock private ColegioRepository colegioRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private RolRepository rolRepository;
    @Mock private TipoPrendaRepository tipoPrendaRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ProductoEtapaProduccionService productoEtapaProduccionService;
    @Mock private CargaTallesService cargaTallesService;
    @Mock private TipoTelaPorDefecto tipoTelaPorDefecto;

    private PedidoImportRowService service;

    private final Usuario pia = Usuario.builder().id(5L).nombre("Pia Gómez").habilitado(true).kommoVendedorEnumId(ENUM_PIA).build();
    private final TipoPrenda campera = TipoPrenda.builder().id(1L).nombre("Campera").build();
    private final TipoPrenda bandera = TipoPrenda.builder().id(5L).nombre("Bandera").build();
    private final TipoTela friza = TipoTela.builder().id(1L).codigo("FRIZA").build();

    @BeforeEach
    void preparar() {
        service = new PedidoImportRowService(pedidoRepository, colegioRepository, usuarioRepository, rolRepository,
            tipoPrendaRepository, passwordEncoder, productoEtapaProduccionService, cargaTallesService, tipoTelaPorDefecto);
    }

    /** La fila que arma KommoLeadMapper para el lead de Andre (24 camperas + bandera, sin email). */
    private static FilaExcelPedido filaAndre(String ficha, Long enumVendedor, Long idLead) {
        return new FilaExcelPedido(28237082, "n5", "pinamar", "bsas", "Primaria", "Andre", "+5492254458408", null,
            "Pia", "SENADO", "2027", ficha, "97000", "2328000", "0", "24", "0", "0", "Si", "Si", "No", "No",
            "30.06.2026", "30.12.2026", "seña + 5", "Adulto", List.of(), enumVendedor, idLead);
    }

    private static FilaExcelPedido filaAndre(String ficha) {
        return filaAndre(ficha, ENUM_PIA, LEAD_ANDRE);
    }

    /** Lo que hace falta para que la fila de Andre pase todas las validaciones. */
    private void catalogoCompleto() {
        lenient().when(usuarioRepository.findByKommoVendedorEnumId(ENUM_PIA)).thenReturn(Optional.of(pia));
        lenient().when(pedidoRepository.findByKommoLeadId(LEAD_ANDRE)).thenReturn(Optional.empty());
        lenient().when(pedidoRepository.existsByCodigoInterno("2027-241")).thenReturn(false);
        lenient().when(rolRepository.findByNombre("ROLE_CLIENTE"))
            .thenReturn(Optional.of(Rol.builder().id(2L).nombre("ROLE_CLIENTE").build()));
        lenient().when(tipoPrendaRepository.findByNombre("Campera")).thenReturn(Optional.of(campera));
        lenient().when(tipoPrendaRepository.findByNombre("Bandera")).thenReturn(Optional.of(bandera));
    }

    @Test
    void desde_kommo_el_vendedor_se_resuelve_por_el_enum_id_vinculado() {
        catalogoCompleto();

        PedidoImportRowService.FilaValidada validada = service.validarFila(filaAndre("241-27"));

        assertThat(validada.vendedor()).isSameAs(pia);
        assertThat(validada.codigoInterno()).isEqualTo("2027-241");
        verify(usuarioRepository, never()).findFirstByNombreContainingIgnoreCaseAndHabilitadoTrue(anyString());
    }

    @Test
    void vendedor_de_kommo_sin_vincular_se_saltea() {
        when(usuarioRepository.findByKommoVendedorEnumId(ENUM_PIA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validarFila(filaAndre("241-27")))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("El vendedor de Kommo 'Pia' no está vinculado a ningún usuario");
    }

    @Test
    void vendedor_de_kommo_vinculado_a_un_usuario_deshabilitado_se_saltea_y_lo_dice() {
        Usuario deshabilitado = Usuario.builder().id(6L).nombre("Pia Vieja").habilitado(false).build();
        when(usuarioRepository.findByKommoVendedorEnumId(ENUM_PIA)).thenReturn(Optional.of(deshabilitado));

        assertThatThrownBy(() -> service.validarFila(filaAndre("241-27")))
            .hasMessage("El vendedor de Kommo 'Pia' está vinculado a Pia Vieja, que está deshabilitado");
    }

    @Test
    void desde_excel_el_vendedor_se_sigue_buscando_por_nombre() {
        catalogoCompleto();
        when(usuarioRepository.findFirstByNombreContainingIgnoreCaseAndHabilitadoTrue("Pia")).thenReturn(Optional.of(pia));

        PedidoImportRowService.FilaValidada validada = service.validarFila(filaAndre("241-27", null, null));

        assertThat(validada.vendedor()).isSameAs(pia);
        verify(usuarioRepository, never()).findByKommoVendedorEnumId(any());
    }

    @Test
    void un_lead_ya_importado_se_detecta_por_su_id_antes_que_por_el_codigo() {
        catalogoCompleto();
        when(pedidoRepository.findByKommoLeadId(LEAD_ANDRE))
            .thenReturn(Optional.of(Pedido.builder().id(1L).codigoInterno("2027-240").build()));

        assertThatThrownBy(() -> service.validarFila(filaAndre("241-27")))
            .hasMessage("El lead #28237082 ya se importó como pedido 2027-240");
        verify(pedidoRepository, never()).existsByCodigoInterno(any());
    }

    @Test
    void un_codigo_repetido_se_sigue_detectando() {
        catalogoCompleto();
        when(pedidoRepository.existsByCodigoInterno("2027-241")).thenReturn(true);

        assertThatThrownBy(() -> service.validarFila(filaAndre("241-27")))
            .hasMessageStartingWith("Ya existe un pedido con el código 2027-241");
    }

    @Test
    void sufijo_de_ficha_con_otro_anio_que_la_promo_se_saltea_mostrando_los_dos() {
        catalogoCompleto();

        assertThatThrownBy(() -> service.validarFila(filaAndre("241-26")))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("'241-26'")
            .hasMessageContaining("2027");
    }

    @Test
    void sufijo_de_ficha_de_cuatro_digitos_se_compara_con_el_anio_completo() {
        catalogoCompleto();

        assertThat(service.validarFila(filaAndre("241-2027")).codigoInterno()).isEqualTo("2027-241");
        assertThatThrownBy(() -> service.validarFila(filaAndre("241-2026"))).hasMessageContaining("Promo 2027");
    }

    @Test
    void sufijo_de_ficha_no_interpretable_se_saltea() {
        catalogoCompleto();

        for (String ficha : List.of("241-A", "241-", "241-027")) {
            assertThatThrownBy(() -> service.validarFila(filaAndre(ficha)))
                .as(ficha)
                .hasMessageStartingWith("Sufijo de Ficha no interpretable");
        }
    }

    @Test
    void ficha_sin_guion_no_se_valida_contra_la_promo() {
        catalogoCompleto();
        when(pedidoRepository.existsByCodigoInterno("2027-2412")).thenReturn(false);

        assertThat(service.validarFila(filaAndre("2412")).codigoInterno()).isEqualTo("2027-2412");
    }

    @Test
    void campos_que_el_lead_no_trae_llegan_en_null_y_no_rompen_la_validacion() {
        catalogoCompleto();
        FilaExcelPedido base = filaAndre("241-27");
        FilaExcelPedido sinBuzosNiRemeras = new FilaExcelPedido(base.filaExcel(), base.colegio(), base.localidad(),
            base.provincia(), base.nivel(), base.contactoPrincipal(), base.telefonoContacto(), base.emailContacto(),
            base.responsableVendedor(), base.etiquetaEstado(), base.promo(), base.fichaKommo(), base.precioUnitario(),
            base.presupuesto(), null, base.camperas(), null, null, base.bandera(), base.contrato(), base.pique(),
            base.tejido(), base.fechaVenta(), base.fechaEntregaPactada(), base.cuotas(), base.responsableCurso(),
            base.notas(), base.vendedorKommoEnumId(), base.kommoLeadId());

        assertThat(service.validarFila(sinBuzosNiRemeras).cantidadPorTipo()).containsExactly(java.util.Map.entry(campera, 24));
    }

    @Test
    void importar_desde_kommo_hace_lo_mismo_que_el_alta_manual_y_marca_origen_kommo_sin_revisar() {
        catalogoCompleto();
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(colegioRepository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(tipoTelaPorDefecto.resolver(campera)).thenReturn(friza);

        Pedido pedido = service.importarFila(filaAndre("241-27"));

        assertThat(pedido.getOrigen()).isEqualTo(OrigenPedido.KOMMO);
        assertThat(pedido.getKommoLeadId()).isEqualTo(LEAD_ANDRE);
        assertThat(pedido.isRevisadoImportacion()).isFalse();
        assertThat(pedido.getEstadoActual()).isEqualTo(EstadoPedido.SENADO);
        assertThat(pedido.getCreadoPor()).isSameAs(pia);
        assertThat(pedido.getHistorial()).singleElement()
            .satisfies(h -> assertThat(h.getObservaciones()).isEqualTo("Alta por importación desde Kommo, lead #28237082"));
        Producto productoCampera = pedido.getProductos().stream()
            .filter(p -> p.getTipoPrenda() == campera).findFirst().orElseThrow();
        assertThat(productoCampera.getTipoTela()).isSameAs(friza);
        assertThat(productoCampera.getCantidadTotal()).isEqualTo(24);

        verify(cargaTallesService).generarLinkParaPedidoNuevo(pedido);
        verify(productoEtapaProduccionService).sincronizarEtapas(productoCampera);
    }

    @Test
    void importar_desde_excel_marca_origen_excel_sin_revisar_y_mantiene_el_texto_del_historial() {
        catalogoCompleto();
        when(usuarioRepository.findFirstByNombreContainingIgnoreCaseAndHabilitadoTrue("Pia")).thenReturn(Optional.of(pia));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(colegioRepository.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        Pedido pedido = service.importarFila(filaAndre("241-27", null, null));

        assertThat(pedido.getOrigen()).isEqualTo(OrigenPedido.EXCEL);
        assertThat(pedido.getKommoLeadId()).isNull();
        assertThat(pedido.isRevisadoImportacion()).isFalse();
        assertThat(pedido.getHistorial()).singleElement()
            .satisfies(h -> assertThat(h.getObservaciones()).isEqualTo("Alta por importación de Excel (Kommo), fila 28237082"));
        verify(cargaTallesService).generarLinkParaPedidoNuevo(pedido);
    }

    @Test
    void si_la_validacion_falla_no_se_guarda_nada() {
        when(usuarioRepository.findByKommoVendedorEnumId(ENUM_PIA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importarFila(filaAndre("241-27"))).isInstanceOf(BusinessRuleException.class);
        verify(colegioRepository, never()).save(any());
        verify(pedidoRepository, never()).save(any());
        verify(cargaTallesService, never()).generarLinkParaPedidoNuevo(any());
    }
}
