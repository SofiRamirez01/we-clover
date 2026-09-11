package com.weclover.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.pieza.PiezaDetalleResponse;
import com.weclover.backend.dto.pieza.PiezaGeometriaCalculoResponse;
import com.weclover.backend.dto.pieza.PiezaResponse;
import com.weclover.backend.dto.pieza.PiezaResumenResponse;
import com.weclover.backend.dto.pieza.SegmentoDto;
import com.weclover.backend.entity.GrupoTalle;
import com.weclover.backend.entity.Pieza;
import com.weclover.backend.entity.PiezaTalle;
import com.weclover.backend.entity.TablaTalle;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.mapper.PiezaMapper;
import com.weclover.backend.repository.GrupoTalleRepository;
import com.weclover.backend.repository.PiezaRepository;
import com.weclover.backend.repository.PiezaTalleRepository;
import com.weclover.backend.repository.TablaTalleRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PiezaService {

    private final PiezaRepository piezaRepository;
    private final PiezaTalleRepository piezaTalleRepository;
    private final GrupoTalleRepository grupoTalleRepository;
    private final TablaTalleRepository tablaTalleRepository;
    private final PiezaMapper piezaMapper;
    private final PiezaGeometriaClient piezaGeometriaClient;
    private final AutorizacionService autorizacionService;
    private final ObjectMapper objectMapper;

    @Transactional
    public PiezaResponse crear(
            String nombre,
            Long idGrupoTalle,
            Long idTalleBase,
            List<SegmentoDto> segmentos,
            boolean simetrica,
            Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);

        GrupoTalle grupoTalle = obtenerGrupoTalle(idGrupoTalle);
        TablaTalle talleBase = obtenerTalleBase(idTalleBase);
        validarTalleBasePerteneceAGrupo(talleBase, grupoTalle);
        validarNombreDisponible(idGrupoTalle, nombre, grupoTalle, null);

        PiezaGeometriaCalculoResponse calculo = piezaGeometriaClient.calcularBase(segmentos, simetrica);

        Pieza pieza = Pieza.builder()
            .nombre(nombre)
            .grupoTalle(grupoTalle)
            .talleBase(talleBase)
            .segmentosBaseJson(objectMapper.writeValueAsString(segmentos))
            .simetrica(simetrica)
            .activo(true)
            .build();
        pieza = piezaRepository.save(pieza);

        PiezaTalle piezaTalleBase = upsertBase(pieza, talleBase, calculo);

        return construirResponse(pieza, piezaTalleBase);
    }

    @Transactional
    public PiezaResponse actualizar(
            Long id,
            String nombre,
            Long idGrupoTalle,
            Long idTalleBase,
            List<SegmentoDto> segmentos,
            boolean simetrica,
            Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);

        Pieza pieza = piezaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("No existe la pieza con id " + id));

        GrupoTalle grupoTalle = obtenerGrupoTalle(idGrupoTalle);
        TablaTalle talleBase = obtenerTalleBase(idTalleBase);
        validarTalleBasePerteneceAGrupo(talleBase, grupoTalle);
        validarNombreDisponible(idGrupoTalle, nombre, grupoTalle, id);

        PiezaGeometriaCalculoResponse calculo = piezaGeometriaClient.calcularBase(segmentos, simetrica);

        pieza.setNombre(nombre);
        pieza.setGrupoTalle(grupoTalle);
        pieza.setTalleBase(talleBase);
        pieza.setSegmentosBaseJson(objectMapper.writeValueAsString(segmentos));
        pieza.setSimetrica(simetrica);
        pieza = piezaRepository.save(pieza);

        PiezaTalle piezaTalleBase = upsertBase(pieza, talleBase, calculo);

        return construirResponse(pieza, piezaTalleBase);
    }

    @Transactional(readOnly = true)
    public PiezaGeometriaCalculoResponse calcularBasePreview(
            List<SegmentoDto> segmentos, boolean simetrica, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);
        return piezaGeometriaClient.calcularBase(segmentos, simetrica);
    }

    @Transactional(readOnly = true)
    public List<PiezaResponse> listarActivas(Long idGrupoTalle, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);

        List<Pieza> piezas = idGrupoTalle != null
            ? piezaRepository.findByGrupoTalle_Id(idGrupoTalle)
            : piezaRepository.findByActivoTrue();
        piezas = piezas.stream().filter(Pieza::isActivo).toList();

        Map<Long, PiezaTalle> basesPorPieza = piezaTalleRepository
            .findByPieza_IdInAndEsBaseTrue(piezas.stream().map(Pieza::getId).toList())
            .stream()
            .collect(Collectors.toMap(fila -> fila.getPieza().getId(), fila -> fila));

        return piezas.stream()
            .map(pieza -> construirResponse(pieza, basesPorPieza.get(pieza.getId())))
            .toList();
    }

    /**
     * Picker de Piezas (Requisito 4.1 Parte 4): versión liviana con la geometría del talle base
     * (para la miniatura) y si ya está graduada por completo, filtrable por grupoTalle y por
     * nombre. Mismo criterio de permisos que listarActivas — administrativo únicamente por
     * ahora (avisar si hace falta sumar ROLE_DISENADOR más adelante).
     */
    @Transactional(readOnly = true)
    public List<PiezaResumenResponse> listarResumen(Long idGrupoTalle, String q, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);

        String busqueda = q == null ? "" : q.trim().toLowerCase();
        List<Pieza> piezas = (idGrupoTalle != null ? piezaRepository.findByGrupoTalle_Id(idGrupoTalle) : piezaRepository.findByActivoTrue())
            .stream()
            .filter(Pieza::isActivo)
            .filter(pieza -> busqueda.isEmpty() || pieza.getNombre().toLowerCase().contains(busqueda))
            .toList();

        return piezas.stream().map(this::construirResumen).toList();
    }

    /** Resumen de una Pieza puntual, reusado tanto por listarResumen (picker) como por el
     * armado de PatronCortePosicionPiezaResponse (ver PatronCorteService). */
    public PiezaResumenResponse construirResumen(Pieza pieza) {
        PiezaTalle base = piezaTalleRepository.findByPieza_IdAndEsBaseTrue(pieza.getId()).orElse(null);
        long totalTalles = tablaTalleRepository.findByGrupoTalleOrderByOrdenAsc(pieza.getGrupoTalle()).size();
        long resueltos = piezaTalleRepository.findByPieza_Id(pieza.getId()).size();

        return new PiezaResumenResponse(
            pieza.getId(),
            pieza.getNombre(),
            pieza.isSimetrica(),
            base != null ? deserializarCoordenadas(base.getCoordenadasJson()) : List.of(),
            base != null ? base.getAnchoCm() : 0,
            base != null ? base.getLargoCm() : 0,
            totalTalles > 0 && resueltos >= totalTalles);
    }

    private List<double[]> deserializarCoordenadas(String json) {
        return List.of(objectMapper.readValue(json, double[][].class));
    }

    /** Detalle completo (incluye los segmentos originales), para reabrir el editor al editar o duplicar. */
    @Transactional(readOnly = true)
    public PiezaDetalleResponse obtenerDetalle(Long id, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);

        Pieza pieza = piezaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("No existe la pieza con id " + id));

        List<SegmentoDto> segmentos = objectMapper.readValue(
            pieza.getSegmentosBaseJson(),
            objectMapper.getTypeFactory().constructCollectionType(List.class, SegmentoDto.class));

        PiezaTalle piezaTalleBase = obtenerBase(pieza.getId());

        return new PiezaDetalleResponse(
            pieza.getId(),
            pieza.getNombre(),
            pieza.getGrupoTalle().getId(),
            pieza.getGrupoTalle().getNombre(),
            pieza.getTalleBase().getId(),
            pieza.getTalleBase().getTalle(),
            segmentos,
            pieza.isSimetrica(),
            piezaTalleBase.getAnchoCm(),
            piezaTalleBase.getLargoCm(),
            pieza.isActivo());
    }

    /**
     * Crea o actualiza la fila PiezaTalle esBase=true de esta pieza con el resultado fresco de
     * calcularBase. Si el talle base cambió respecto de una edición anterior, la fila que era
     * base en el talle viejo deja de serlo (pero conserva su geometría, que queda desactualizada
     * hasta que se la regenere desde la pantalla de graduación).
     */
    private PiezaTalle upsertBase(Pieza pieza, TablaTalle talleBase, PiezaGeometriaCalculoResponse calculo) {
        piezaTalleRepository.findByPieza_IdAndEsBaseTrue(pieza.getId())
            .filter(fila -> !fila.getTalle().getId().equals(talleBase.getId()))
            .ifPresent(fila -> {
                fila.setEsBase(false);
                piezaTalleRepository.save(fila);
            });

        PiezaTalle piezaTalleBase = piezaTalleRepository.findByPieza_IdAndTalle_Id(pieza.getId(), talleBase.getId())
            .orElseGet(() -> PiezaTalle.builder().pieza(pieza).talle(talleBase).build());

        piezaTalleBase.setCoordenadasJson(objectMapper.writeValueAsString(calculo.coordenadas()));
        piezaTalleBase.setAreaCm2(calculo.areaCm2());
        piezaTalleBase.setAnchoCm(calculo.anchoCm());
        piezaTalleBase.setLargoCm(calculo.largoCm());
        piezaTalleBase.setPerimetroCm(calculo.perimetroCm());
        piezaTalleBase.setEsBase(true);
        piezaTalleBase.setEditadoManualmente(false);
        piezaTalleBase.setFechaGeneracion(LocalDateTime.now());

        return piezaTalleRepository.save(piezaTalleBase);
    }

    private PiezaTalle obtenerBase(Long idPieza) {
        return piezaTalleRepository.findByPieza_IdAndEsBaseTrue(idPieza)
            .orElseThrow(() -> new ResourceNotFoundException(
                "La pieza con id " + idPieza + " todavía no tiene generada la geometría de su talle base"));
    }

    private PiezaResponse construirResponse(Pieza pieza, PiezaTalle piezaTalleBase) {
        PiezaResponse mapeada = piezaMapper.toResponse(pieza);
        double anchoBaseCm = piezaTalleBase != null ? piezaTalleBase.getAnchoCm() : 0;
        double largoBaseCm = piezaTalleBase != null ? piezaTalleBase.getLargoCm() : 0;
        return new PiezaResponse(
            mapeada.id(), mapeada.nombre(), mapeada.idGrupoTalle(), mapeada.nombreGrupoTalle(),
            mapeada.idTalleBase(), mapeada.talleBase(), mapeada.simetrica(),
            anchoBaseCm, largoBaseCm, mapeada.activo());
    }

    private GrupoTalle obtenerGrupoTalle(Long idGrupoTalle) {
        return grupoTalleRepository.findById(idGrupoTalle)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el grupo de talle con id " + idGrupoTalle));
    }

    private TablaTalle obtenerTalleBase(Long idTalleBase) {
        return tablaTalleRepository.findById(idTalleBase)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el talle con id " + idTalleBase));
    }

    private void validarTalleBasePerteneceAGrupo(TablaTalle talleBase, GrupoTalle grupoTalle) {
        if (!talleBase.getGrupoTalle().getId().equals(grupoTalle.getId())) {
            throw new BusinessRuleException("El talle base debe pertenecer al grupo de talle seleccionado");
        }
    }

    /** idPiezaExcluida es null al crear (no hay nada que excluir); es el propio id al editar. */
    private void validarNombreDisponible(Long idGrupoTalle, String nombre, GrupoTalle grupoTalle, Long idPiezaExcluida) {
        boolean yaExiste = idPiezaExcluida == null
            ? piezaRepository.findByGrupoTalle_IdAndNombre(idGrupoTalle, nombre).isPresent()
            : piezaRepository.findByGrupoTalle_IdAndNombreAndIdNot(idGrupoTalle, nombre, idPiezaExcluida).isPresent();

        if (yaExiste) {
            throw new BusinessRuleException(
                "Ya existe una pieza llamada '" + nombre + "' en el grupo de talle " + grupoTalle.getNombre());
        }
    }
}
