package com.weclover.backend.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.weclover.backend.dto.corte.PreparacionCorteResponse;
import com.weclover.backend.dto.corte.optimizacion.CrearTrabajoCorteDto;
import com.weclover.backend.dto.corte.optimizacion.OptimizarCorteRequest;
import com.weclover.backend.dto.corte.optimizacion.TrabajoCorteCreadoResponse;
import com.weclover.backend.dto.corte.optimizacion.TrabajoCorteResponse;
import com.weclover.backend.dto.corte.optimizacion.TrabajoCorteServicioDto;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.repository.TandaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Optimización de los cortes de una tanda (Módulo 4, paso 3): arma la demanda con
 * PreparacionCorteService, la manda al optimizador de cortes y devuelve el id del trabajo; después
 * se consulta su estado, avance y resultado. Generar es solo para ADMINISTRATIVO; consultar,
 * también para PLANTA. No se guarda nada todavía (las tizadas no se persisten).
 */
@Service
@RequiredArgsConstructor
public class CorteOptimizacionService {

    private static final Set<String> ROLES_LECTURA = Set.of("ROLE_ADMINISTRATIVO", "ROLE_PLANTA");

    private final PreparacionCorteService preparacionCorteService;
    private final CorteOptimizadorClient corteOptimizadorClient;
    private final AutorizacionService autorizacionService;
    private final TandaRepository tandaRepository;

    public TrabajoCorteCreadoResponse optimizar(Long idTanda, OptimizarCorteRequest opciones, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);
        PreparacionCorteResponse preparacion = preparacionCorteService.preparar(idTanda, idUsuarioActor);
        boolean hayDemanda = preparacion.grupos().stream().anyMatch(grupo -> !grupo.items().isEmpty());
        if (!hayDemanda) {
            throw new BusinessRuleException("La tanda " + preparacion.nombreTanda() + " no tiene demanda para cortar"
                + (preparacion.resumen().unidadesPendientes() > 0
                    ? " (" + preparacion.resumen().unidadesPendientes() + " unidades pendientes: ver la preparación del corte)"
                    : ""));
        }
        TrabajoCorteServicioDto creado = corteOptimizadorClient.crearTrabajo(
            new CrearTrabajoCorteDto(preparacion, parametros(opciones)));
        return new TrabajoCorteCreadoResponse(creado.idTrabajo(), idTanda, creado.estado(), creado.posicionEnCola());
    }

    public TrabajoCorteResponse consultar(Long idTanda, String idTrabajo, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_LECTURA);
        return aRespuesta(trabajoDeLaTanda(idTanda, idTrabajo));
    }

    /** Cancela un trabajo en cola o en curso. Solo ADMINISTRATIVO. 409 si ya terminó. */
    public TrabajoCorteResponse cancelar(Long idTanda, String idTrabajo, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);
        trabajoDeLaTanda(idTanda, idTrabajo);
        return aRespuesta(corteOptimizadorClient.cancelarTrabajo(idTrabajo));
    }

    private TrabajoCorteServicioDto trabajoDeLaTanda(Long idTanda, String idTrabajo) {
        if (!tandaRepository.existsById(idTanda)) {
            throw new ResourceNotFoundException("No existe la tanda con id " + idTanda);
        }
        TrabajoCorteServicioDto trabajo = corteOptimizadorClient.consultarTrabajo(idTrabajo);
        if (!Objects.equals(trabajo.idTanda(), idTanda)) {
            // El id de trabajo es de otra tanda: para esta tanda no existe.
            throw new ResourceNotFoundException("No existe el trabajo de corte " + idTrabajo + " para la tanda " + idTanda);
        }
        return trabajo;
    }

    private static TrabajoCorteResponse aRespuesta(TrabajoCorteServicioDto trabajo) {
        return new TrabajoCorteResponse(trabajo.idTrabajo(), trabajo.idTanda(), trabajo.estado(), trabajo.posicionEnCola(),
            trabajo.progreso(), instante(trabajo.creado()), instante(trabajo.iniciado()), instante(trabajo.terminado()),
            instante(trabajo.venceEn()), trabajo.error(), trabajo.resultado());
    }

    /** Solo las opciones que expone el backend; el resto de los parámetros vienen en la preparación. */
    private Map<String, Object> parametros(OptimizarCorteRequest opciones) {
        Map<String, Object> parametros = new LinkedHashMap<>();
        if (opciones == null) {
            return parametros;
        }
        if (opciones.modoTiempo() != null) {
            parametros.put("modoTiempo", opciones.modoTiempo());
        }
        if (opciones.presupuestoSegundosPorCorte() != null) {
            parametros.put("presupuestoSegundosPorCorte", opciones.presupuestoSegundosPorCorte());
        }
        if (opciones.excedenteMaximoPorItem() != null) {
            parametros.put("excedenteMaximoPorItem", opciones.excedenteMaximoPorItem());
        }
        if (opciones.excedenteMaximoPorTela() != null && !opciones.excedenteMaximoPorTela().isEmpty()) {
            parametros.put("excedenteMaximoPorTela", opciones.excedenteMaximoPorTela());
        }
        return parametros;
    }

    private static Instant instante(Double segundosEpoch) {
        return segundosEpoch == null ? null : Instant.ofEpochMilli(Math.round(segundosEpoch * 1000));
    }
}
