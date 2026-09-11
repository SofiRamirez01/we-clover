package com.weclover.backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.piezatalle.PiezaTalleBatchItemRequest;
import com.weclover.backend.dto.piezatalle.PiezaTalleBatchRequest;
import com.weclover.backend.dto.piezatalle.PiezaTalleResponse;
import com.weclover.backend.dto.piezatalle.PiezaTalleUpsertRequest;
import com.weclover.backend.entity.Pieza;
import com.weclover.backend.entity.PiezaTalle;
import com.weclover.backend.entity.TablaTalle;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.repository.PiezaRepository;
import com.weclover.backend.repository.PiezaTalleRepository;
import com.weclover.backend.repository.TablaTalleRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PiezaTalleService {

    private final PiezaTalleRepository piezaTalleRepository;
    private final PiezaRepository piezaRepository;
    private final TablaTalleRepository tablaTalleRepository;
    private final AutorizacionService autorizacionService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<PiezaTalleResponse> listar(Long idPieza, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);
        obtenerPieza(idPieza);
        return piezaTalleRepository.findByPieza_Id(idPieza).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public PiezaTalleResponse upsert(Long idPieza, Long idTalle, PiezaTalleUpsertRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);
        Pieza pieza = obtenerPieza(idPieza);
        TablaTalle talle = obtenerTalle(idTalle);
        validarTallePerteneceAGrupo(talle, pieza);
        validarCoordenadas(request.coordenadas());

        PiezaTalle piezaTalle = piezaTalleRepository.findByPieza_IdAndTalle_Id(idPieza, idTalle)
            .orElseGet(() -> PiezaTalle.builder().pieza(pieza).talle(talle).build());

        aplicarDatos(piezaTalle, request.coordenadas(), request.areaCm2(), request.anchoCm(),
            request.largoCm(), request.perimetroCm(), request.esBase(), request.editadoManualmente());

        return toResponse(piezaTalleRepository.save(piezaTalle));
    }

    @Transactional
    public List<PiezaTalleResponse> batchUpsert(Long idPieza, PiezaTalleBatchRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);
        Pieza pieza = obtenerPieza(idPieza);

        List<PiezaTalle> filas = new ArrayList<>();
        for (PiezaTalleBatchItemRequest item : request.talles()) {
            TablaTalle talle = obtenerTalle(item.idTalle());
            validarTallePerteneceAGrupo(talle, pieza);
            validarCoordenadas(item.coordenadas());

            PiezaTalle piezaTalle = piezaTalleRepository.findByPieza_IdAndTalle_Id(idPieza, item.idTalle())
                .orElseGet(() -> PiezaTalle.builder().pieza(pieza).talle(talle).build());
            if (piezaTalle.isEsBase()) {
                throw new BusinessRuleException(
                    "No se puede sobrescribir el talle base (" + talle.getTalle() + ") mediante el batch de graduación");
            }
            aplicarDatos(piezaTalle, item.coordenadas(), item.areaCm2(), item.anchoCm(),
                item.largoCm(), item.perimetroCm(), false, item.editadoManualmente());
            filas.add(piezaTalle);
        }

        return piezaTalleRepository.saveAll(filas).stream().map(this::toResponse).toList();
    }

    @Transactional
    public PiezaTalleResponse revertir(Long idPieza, Long idTalle, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);
        obtenerPieza(idPieza);

        PiezaTalle piezaTalle = piezaTalleRepository.findByPieza_IdAndTalle_Id(idPieza, idTalle)
            .orElseThrow(() -> new ResourceNotFoundException("Todavía no hay datos generados para ese talle en esta pieza"));

        // Sin efecto sobre la fila esBase=true (revertir el talle base no tiene sentido: no hay
        // ninguna versión "automática" distinta a la que el usuario ya dibujó).
        if (!piezaTalle.isEsBase() && piezaTalle.isEditadoManualmente()) {
            piezaTalle.setEditadoManualmente(false);
            piezaTalleRepository.save(piezaTalle);
        }

        return toResponse(piezaTalle);
    }

    private Pieza obtenerPieza(Long idPieza) {
        return piezaRepository.findById(idPieza)
            .orElseThrow(() -> new ResourceNotFoundException("No existe la pieza con id " + idPieza));
    }

    private TablaTalle obtenerTalle(Long idTalle) {
        return tablaTalleRepository.findById(idTalle)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el talle con id " + idTalle));
    }

    private void validarTallePerteneceAGrupo(TablaTalle talle, Pieza pieza) {
        if (!talle.getGrupoTalle().getId().equals(pieza.getGrupoTalle().getId())) {
            throw new BusinessRuleException(
                "El talle " + talle.getTalle() + " no pertenece al grupo de talle de esta pieza");
        }
    }

    private void validarCoordenadas(List<double[]> coordenadas) {
        if (coordenadas == null || coordenadas.isEmpty()) {
            throw new BusinessRuleException("Las coordenadas del contorno no pueden estar vacías");
        }
    }

    private void aplicarDatos(
            PiezaTalle piezaTalle,
            List<double[]> coordenadas,
            double areaCm2,
            double anchoCm,
            double largoCm,
            double perimetroCm,
            boolean esBase,
            boolean editadoManualmente) {
        piezaTalle.setCoordenadasJson(objectMapper.writeValueAsString(coordenadas));
        piezaTalle.setAreaCm2(areaCm2);
        piezaTalle.setAnchoCm(anchoCm);
        piezaTalle.setLargoCm(largoCm);
        piezaTalle.setPerimetroCm(perimetroCm);
        piezaTalle.setEsBase(esBase);
        piezaTalle.setEditadoManualmente(editadoManualmente);
        piezaTalle.setFechaGeneracion(LocalDateTime.now());
    }

    PiezaTalleResponse toResponse(PiezaTalle piezaTalle) {
        return new PiezaTalleResponse(
            piezaTalle.getTalle().getId(),
            piezaTalle.getTalle().getTalle(),
            deserializarCoordenadas(piezaTalle.getCoordenadasJson()),
            piezaTalle.getAreaCm2(),
            piezaTalle.getAnchoCm(),
            piezaTalle.getLargoCm(),
            piezaTalle.getPerimetroCm(),
            piezaTalle.isEsBase(),
            piezaTalle.isEditadoManualmente(),
            piezaTalle.getFechaGeneracion());
    }

    private List<double[]> deserializarCoordenadas(String json) {
        return new ArrayList<>(List.of(objectMapper.readValue(json, double[][].class)));
    }
}
