package com.weclover.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.patroncorte.PatronCortePosicionPiezaRequest;
import com.weclover.backend.dto.patroncorte.PatronCortePosicionPiezaResponse;
import com.weclover.backend.service.PatronCortePosicionPiezaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Pines de Piezas sobre la imagen de un color de moldería — Requisito 4.1 Parte 4. */
@RestController
@RequestMapping("/api/patrones-corte/{idPatronCorte}/colores/{idColor}/piezas")
@RequiredArgsConstructor
public class PatronCortePosicionPiezaController {

    private final PatronCortePosicionPiezaService service;

    @PostMapping
    public ResponseEntity<PatronCortePosicionPiezaResponse> agregar(
            @PathVariable Long idPatronCorte,
            @PathVariable Long idColor,
            @Valid @RequestBody PatronCortePosicionPiezaRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        PatronCortePosicionPiezaResponse response = service.agregar(idPatronCorte, idColor, request, idUsuarioActor);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{idPosicion}")
    public PatronCortePosicionPiezaResponse actualizar(
            @PathVariable Long idPatronCorte,
            @PathVariable Long idColor,
            @PathVariable Long idPosicion,
            @Valid @RequestBody PatronCortePosicionPiezaRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return service.actualizar(idPatronCorte, idColor, idPosicion, request, idUsuarioActor);
    }

    @DeleteMapping("/{idPosicion}")
    public ResponseEntity<Void> eliminar(
            @PathVariable Long idPatronCorte,
            @PathVariable Long idColor,
            @PathVariable Long idPosicion,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        service.eliminar(idPatronCorte, idColor, idPosicion, idUsuarioActor);
        return ResponseEntity.noContent().build();
    }
}
