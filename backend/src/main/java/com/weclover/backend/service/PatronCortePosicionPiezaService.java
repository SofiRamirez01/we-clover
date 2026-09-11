package com.weclover.backend.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.patroncorte.PatronCortePosicionPiezaRequest;
import com.weclover.backend.dto.patroncorte.PatronCortePosicionPiezaResponse;
import com.weclover.backend.entity.GrupoTalle;
import com.weclover.backend.entity.PatronCorte;
import com.weclover.backend.entity.PatronCorteColor;
import com.weclover.backend.entity.PatronCortePosicionPieza;
import com.weclover.backend.entity.Pieza;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.repository.PatronCorteRepository;
import com.weclover.backend.repository.PatronCortePosicionPiezaRepository;
import com.weclover.backend.repository.PiezaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Asignación de Piezas físicas a posiciones (pines) sobre la imagen de un PatronCorteColor —
 * Requisito 4.1 Parte 4. La LECTURA/localización de un color o una posición navega siempre
 * desde el agregado PatronCorte (no hay repositorio propio de PatronCorteColor, ver
 * PatronCorteService, y así de paso se valida que colorId/posicionId realmente pertenezcan al
 * patronCorteId de la URL). Pero la MUTACIÓN de una PatronCortePosicionPieza puntual se hace vía
 * su propio repositorio, no reasignando `patronCorte.colores` y guardando el agregado: éste ya
 * está *attached* (viene de un findById en la misma transacción), así que guardar el agregado
 * dispara un merge() en vez de un persist() sobre el hijo nuevo — y merge() no vuelve a escribir
 * el id generado sobre la instancia en memoria que ya se le devolvió al controller.
 */
@Service
@RequiredArgsConstructor
public class PatronCortePosicionPiezaService {

    private final PatronCorteRepository patronCorteRepository;
    private final PatronCortePosicionPiezaRepository patronCortePosicionPiezaRepository;
    private final PiezaRepository piezaRepository;
    private final PiezaService piezaService;
    private final AutorizacionService autorizacionService;

    @Transactional
    public PatronCortePosicionPiezaResponse agregar(
            Long idPatronCorte, Long idColor, PatronCortePosicionPiezaRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);

        PatronCorteColor color = obtenerColor(idPatronCorte, idColor);
        Pieza pieza = obtenerPieza(request.piezaId());
        validarPiezaCompatible(color.getPatronCorte(), pieza);
        validarCoordenada(request.coordenadaXPin());
        validarCoordenada(request.coordenadaYPin());

        PatronCortePosicionPieza posicion = PatronCortePosicionPieza.builder()
            .patronCorteColor(color)
            .pieza(pieza)
            .coordenadaXPin(request.coordenadaXPin())
            .coordenadaYPin(request.coordenadaYPin())
            .etiqueta(request.etiqueta())
            .fechaCreacion(LocalDateTime.now())
            .build();
        posicion = patronCortePosicionPiezaRepository.save(posicion);

        return toResponse(posicion);
    }

    @Transactional
    public PatronCortePosicionPiezaResponse actualizar(
            Long idPatronCorte, Long idColor, Long idPosicion, PatronCortePosicionPiezaRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);

        PatronCorteColor color = obtenerColor(idPatronCorte, idColor);
        PatronCortePosicionPieza posicion = obtenerPosicion(color, idPosicion);
        Pieza pieza = obtenerPieza(request.piezaId());
        validarPiezaCompatible(color.getPatronCorte(), pieza);
        validarCoordenada(request.coordenadaXPin());
        validarCoordenada(request.coordenadaYPin());

        posicion.setPieza(pieza);
        posicion.setCoordenadaXPin(request.coordenadaXPin());
        posicion.setCoordenadaYPin(request.coordenadaYPin());
        posicion.setEtiqueta(request.etiqueta());
        posicion = patronCortePosicionPiezaRepository.save(posicion);

        return toResponse(posicion);
    }

    /**
     * A diferencia de agregar/actualizar, acá SÍ hay que pasar por la colección del padre (y no
     * por un delete() directo contra el repositorio del hijo): `color` ya viene con
     * `posicionesPieza` cargada e inicializada en esta misma transacción (ver obtenerColor), y
     * con orphanRemoval=true Hibernate decide qué borrar comparando esa colección contra su
     * snapshot al hacer flush — si el hijo se borra por su cuenta pero sigue apareciendo en la
     * colección ya cargada del padre, Hibernate no detecta ningún cambio y el borrado se pierde
     * en silencio (sin excepción).
     */
    @Transactional
    public void eliminar(Long idPatronCorte, Long idColor, Long idPosicion, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);

        PatronCorteColor color = obtenerColor(idPatronCorte, idColor);
        PatronCortePosicionPieza posicion = obtenerPosicion(color, idPosicion);
        color.getPosicionesPieza().remove(posicion);
    }

    private PatronCorteColor obtenerColor(Long idPatronCorte, Long idColor) {
        PatronCorte patronCorte = patronCorteRepository.findById(idPatronCorte)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el patrón de corte con id " + idPatronCorte));
        return patronCorte.getColores().stream()
            .filter(color -> color.getId().equals(idColor))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException(
                "El color " + idColor + " no pertenece al patrón de corte " + idPatronCorte));
    }

    private PatronCortePosicionPieza obtenerPosicion(PatronCorteColor color, Long idPosicion) {
        return color.getPosicionesPieza().stream()
            .filter(posicion -> posicion.getId().equals(idPosicion))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("No existe la posición " + idPosicion + " en ese color"));
    }

    private Pieza obtenerPieza(Long idPieza) {
        return piezaRepository.findById(idPieza)
            .orElseThrow(() -> new ResourceNotFoundException("No existe la pieza con id " + idPieza));
    }

    private void validarCoordenada(double valor) {
        if (valor < 0.0 || valor > 1.0) {
            throw new BusinessRuleException("La posición del pin debe ser una fracción entre 0.0 y 1.0");
        }
    }

    private void validarPiezaCompatible(PatronCorte patronCorte, Pieza pieza) {
        GrupoTalle grupoTalle = PatronCorteGrupoTalleResolver.resolverOLanzar(patronCorte);
        if (!pieza.getGrupoTalle().getId().equals(grupoTalle.getId())) {
            throw new BusinessRuleException(
                "La pieza '" + pieza.getNombre() + "' no pertenece al grupo de talle de esta moldería");
        }
    }

    private PatronCortePosicionPiezaResponse toResponse(PatronCortePosicionPieza posicion) {
        return new PatronCortePosicionPiezaResponse(
            posicion.getId(),
            posicion.getCoordenadaXPin(),
            posicion.getCoordenadaYPin(),
            posicion.getEtiqueta(),
            piezaService.construirResumen(posicion.getPieza()));
    }
}
