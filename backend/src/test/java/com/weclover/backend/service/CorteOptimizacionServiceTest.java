package com.weclover.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.json.JsonMapper;
import com.weclover.backend.dto.corte.CantidadPorColorResponse;
import com.weclover.backend.dto.corte.GrupoTelaCorteResponse;
import com.weclover.backend.dto.corte.ItemCorteResponse;
import com.weclover.backend.dto.corte.ParametrosCorteResponse;
import com.weclover.backend.dto.corte.PreparacionCorteResponse;
import com.weclover.backend.dto.corte.ResumenCorteResponse;
import com.weclover.backend.dto.corte.optimizacion.CrearTrabajoCorteDto;
import com.weclover.backend.dto.corte.optimizacion.OptimizarCorteRequest;
import com.weclover.backend.dto.corte.optimizacion.ProgresoCorteResponse;
import com.weclover.backend.dto.corte.optimizacion.TrabajoCorteCreadoResponse;
import com.weclover.backend.dto.corte.optimizacion.TrabajoCorteResponse;
import com.weclover.backend.dto.corte.optimizacion.TrabajoCorteServicioDto;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ForbiddenException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.exception.ServicioNoDisponibleException;
import com.weclover.backend.repository.TandaRepository;

class CorteOptimizacionServiceTest {

    private static final long ADMIN = 14L;
    private static final long PLANTA = 20L;
    private static final long TANDA = 1L;

    private final PreparacionCorteService preparacionCorteService = mock(PreparacionCorteService.class);
    private final CorteOptimizadorClient client = mock(CorteOptimizadorClient.class);
    private final AutorizacionService autorizacionService = mock(AutorizacionService.class);
    private final TandaRepository tandaRepository = mock(TandaRepository.class);
    private final CorteOptimizacionService service =
        new CorteOptimizacionService(preparacionCorteService, client, autorizacionService, tandaRepository);

    private static PreparacionCorteResponse preparacion(boolean conDemanda, int pendientes) {
        List<ItemCorteResponse> items = conDemanda
            ? List.of(new ItemCorteResponse(5L, 2L, "2", List.of(new CantidadPorColorResponse(1L, "Verde", null, 3)), 3, List.of()))
            : List.of();
        return new PreparacionCorteResponse(TANDA, "A", new ParametrosCorteResponse(90, 800, 0.5, 20, 2), List.of(),
            List.of(new GrupoTelaCorteResponse(1L, "FRIZA", "Frisa", items)), List.of(),
            new ResumenCorteResponse(conDemanda ? 3 : 0, pendientes, conDemanda ? 1 : 0, List.of(), "", List.of()));
    }

    private static TrabajoCorteServicioDto trabajo(Long idTanda, String estado) {
        return new TrabajoCorteServicioDto("abc", idTanda, estado, null,
            new ProgresoCorteResponse(1, 2, 1, 7L, 50, 200, 300.5, 0.66, 12.0),
            1760900000.5, 1760900001.0, null, null, null,
            "TERMINADO".equals(estado) ? JsonMapper.builder().build().readTree("{\"cortes\":[]}") : null);
    }

    // --- Lanzar ---------------------------------------------------------------------------------

    @Test
    void optimizar_arma_la_demanda_y_manda_solo_las_opciones_del_backend() {
        PreparacionCorteResponse preparacion = preparacion(true, 0);
        when(preparacionCorteService.preparar(TANDA, ADMIN)).thenReturn(preparacion);
        when(client.crearTrabajo(any())).thenReturn(new TrabajoCorteServicioDto(
            "abc", null, "EN_COLA", 1, null, null, null, null, null, null, null));

        TrabajoCorteCreadoResponse creado = service.optimizar(TANDA,
            new OptimizarCorteRequest("RAPIDO", 30, 0, Map.of("RIBB", 2)), ADMIN);

        assertThat(creado).isEqualTo(new TrabajoCorteCreadoResponse("abc", TANDA, "EN_COLA", 1));
        ArgumentCaptor<CrearTrabajoCorteDto> enviado = ArgumentCaptor.forClass(CrearTrabajoCorteDto.class);
        verify(client).crearTrabajo(enviado.capture());
        assertThat(enviado.getValue().preparacion()).isSameAs(preparacion);
        assertThat(enviado.getValue().parametros()).containsExactly(
            Map.entry("modoTiempo", "RAPIDO"), Map.entry("presupuestoSegundosPorCorte", 30),
            Map.entry("excedenteMaximoPorItem", 0), Map.entry("excedenteMaximoPorTela", Map.of("RIBB", 2)));
    }

    @Test
    void optimizar_sin_opciones_manda_parametros_vacios() {
        when(preparacionCorteService.preparar(TANDA, ADMIN)).thenReturn(preparacion(true, 0));
        when(client.crearTrabajo(any())).thenReturn(new TrabajoCorteServicioDto(
            "abc", null, "EN_COLA", 1, null, null, null, null, null, null, null));

        service.optimizar(TANDA, null, ADMIN);

        ArgumentCaptor<CrearTrabajoCorteDto> enviado = ArgumentCaptor.forClass(CrearTrabajoCorteDto.class);
        verify(client).crearTrabajo(enviado.capture());
        assertThat(enviado.getValue().parametros()).isEmpty();
    }

    @Test
    void sin_demanda_para_cortar_no_llama_al_optimizador() {
        when(preparacionCorteService.preparar(TANDA, ADMIN)).thenReturn(preparacion(false, 23));

        assertThatThrownBy(() -> service.optimizar(TANDA, null, ADMIN))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("no tiene demanda para cortar")
            .hasMessageContaining("23 unidades pendientes");
        verifyNoInteractions(client);
    }

    @Test
    void planta_no_puede_lanzar_una_optimizacion() {
        doThrow(new ForbiddenException("Esta acción requiere uno de estos roles: ROLE_ADMINISTRATIVO"))
            .when(autorizacionService).verificarRolAdministrativo(PLANTA);

        assertThatThrownBy(() -> service.optimizar(TANDA, null, PLANTA)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(preparacionCorteService, client);
    }

    @Test
    void si_el_optimizador_no_responde_el_error_llega_tal_cual() {
        when(preparacionCorteService.preparar(TANDA, ADMIN)).thenReturn(preparacion(true, 0));
        when(client.crearTrabajo(any())).thenThrow(new ServicioNoDisponibleException("El optimizador de cortes no responde"));

        assertThatThrownBy(() -> service.optimizar(TANDA, null, ADMIN))
            .isInstanceOf(ServicioNoDisponibleException.class)
            .hasMessageContaining("no responde");
    }

    // --- Consultar ------------------------------------------------------------------------------

    @Test
    void planta_puede_consultar_y_se_reenvian_estado_avance_y_resultado() {
        when(tandaRepository.existsById(TANDA)).thenReturn(true);
        when(client.consultarTrabajo("abc")).thenReturn(trabajo(TANDA, "TERMINADO"));

        TrabajoCorteResponse vista = service.consultar(TANDA, "abc", PLANTA);

        verify(autorizacionService).verificarRolPermitido(eq(PLANTA), eq(java.util.Set.of("ROLE_ADMINISTRATIVO", "ROLE_PLANTA")));
        assertThat(vista.estado()).isEqualTo("TERMINADO");
        assertThat(vista.progreso().mejorLargoCm()).isEqualTo(300.5);
        assertThat(vista.creado()).isEqualTo(Instant.ofEpochMilli(1760900000500L));
        assertThat(vista.terminado()).isNull();
        assertThat(vista.resultado().has("cortes")).isTrue();
    }

    @Test
    void un_trabajo_de_otra_tanda_no_existe_para_esta() {
        when(tandaRepository.existsById(TANDA)).thenReturn(true);
        when(client.consultarTrabajo("abc")).thenReturn(trabajo(99L, "EJECUTANDO"));

        assertThatThrownBy(() -> service.consultar(TANDA, "abc", ADMIN))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("para la tanda 1");
    }

    @Test
    void consultar_una_tanda_inexistente_es_404_sin_llamar_al_optimizador() {
        when(tandaRepository.existsById(anyLong())).thenReturn(false);

        assertThatThrownBy(() -> service.consultar(TANDA, "abc", ADMIN)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(client);
    }

    @Test
    void administrativo_cancela_un_trabajo_de_su_tanda() {
        when(tandaRepository.existsById(TANDA)).thenReturn(true);
        when(client.consultarTrabajo("abc")).thenReturn(trabajo(TANDA, "EJECUTANDO"));
        when(client.cancelarTrabajo("abc")).thenReturn(trabajo(TANDA, "CANCELADO"));

        assertThat(service.cancelar(TANDA, "abc", ADMIN).estado()).isEqualTo("CANCELADO");
        verify(autorizacionService).verificarRolAdministrativo(ADMIN);
    }

    @Test
    void no_se_cancela_un_trabajo_de_otra_tanda() {
        when(tandaRepository.existsById(TANDA)).thenReturn(true);
        when(client.consultarTrabajo("abc")).thenReturn(trabajo(99L, "EJECUTANDO"));

        assertThatThrownBy(() -> service.cancelar(TANDA, "abc", ADMIN)).isInstanceOf(ResourceNotFoundException.class);
        verify(client, org.mockito.Mockito.never()).cancelarTrabajo(any());
    }

    @Test
    void planta_no_puede_cancelar() {
        doThrow(new ForbiddenException("Esta acción requiere uno de estos roles: ROLE_ADMINISTRATIVO"))
            .when(autorizacionService).verificarRolAdministrativo(PLANTA);

        assertThatThrownBy(() -> service.cancelar(TANDA, "abc", PLANTA)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(client);
    }

    @Test
    void un_cliente_no_puede_consultar() {
        doThrow(new ForbiddenException("Esta acción requiere uno de estos roles"))
            .when(autorizacionService).verificarRolPermitido(eq(2L), anySet());

        assertThatThrownBy(() -> service.consultar(TANDA, "abc", 2L)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(client, tandaRepository);
    }
}
