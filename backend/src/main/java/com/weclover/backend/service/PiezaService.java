package com.weclover.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.pieza.PiezaDetalleResponse;
import com.weclover.backend.dto.pieza.PiezaGeometriaCalculoResponse;
import com.weclover.backend.dto.pieza.PiezaResponse;
import com.weclover.backend.dto.pieza.SegmentoDto;
import com.weclover.backend.entity.GrupoTalle;
import com.weclover.backend.entity.Pieza;
import com.weclover.backend.entity.TablaTalle;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.mapper.PiezaMapper;
import com.weclover.backend.repository.GrupoTalleRepository;
import com.weclover.backend.repository.PiezaRepository;
import com.weclover.backend.repository.TablaTalleRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PiezaService {

    private final PiezaRepository piezaRepository;
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
            .coordenadasBaseJson(objectMapper.writeValueAsString(calculo.coordenadas()))
            .anchoBaseCm(calculo.anchoCm())
            .largoBaseCm(calculo.largoCm())
            .simetrica(simetrica)
            .activo(true)
            .build();

        return piezaMapper.toResponse(piezaRepository.save(pieza));
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
        pieza.setCoordenadasBaseJson(objectMapper.writeValueAsString(calculo.coordenadas()));
        pieza.setAnchoBaseCm(calculo.anchoCm());
        pieza.setLargoBaseCm(calculo.largoCm());
        pieza.setSimetrica(simetrica);

        return piezaMapper.toResponse(piezaRepository.save(pieza));
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

        return piezas.stream()
            .filter(Pieza::isActivo)
            .map(piezaMapper::toResponse)
            .toList();
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

        return new PiezaDetalleResponse(
            pieza.getId(),
            pieza.getNombre(),
            pieza.getGrupoTalle().getId(),
            pieza.getGrupoTalle().getNombre(),
            pieza.getTalleBase().getId(),
            pieza.getTalleBase().getTalle(),
            segmentos,
            pieza.isSimetrica(),
            pieza.getAnchoBaseCm(),
            pieza.getLargoBaseCm(),
            pieza.isActivo());
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
