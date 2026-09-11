package com.weclover.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.piezatalle.PiezaTalleBatchRequest;
import com.weclover.backend.dto.piezatalle.PiezaTalleResponse;
import com.weclover.backend.dto.piezatalle.PiezaTalleUpsertRequest;
import com.weclover.backend.service.PiezaTalleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Graduación automática de una Pieza por talle (Requisito 4.1, Parte 3). Ver PiezaTalle. */
@RestController
@RequestMapping("/api/piezas/{idPieza}/talles")
@RequiredArgsConstructor
public class PiezaTalleController {

    private final PiezaTalleService piezaTalleService;

    @GetMapping
    public List<PiezaTalleResponse> listar(
            @PathVariable Long idPieza,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return piezaTalleService.listar(idPieza, idUsuarioActor);
    }

    @PutMapping("/{idTalle}")
    public PiezaTalleResponse upsert(
            @PathVariable Long idPieza,
            @PathVariable Long idTalle,
            @Valid @RequestBody PiezaTalleUpsertRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return piezaTalleService.upsert(idPieza, idTalle, request, idUsuarioActor);
    }

    @PostMapping("/batch")
    public List<PiezaTalleResponse> batchUpsert(
            @PathVariable Long idPieza,
            @Valid @RequestBody PiezaTalleBatchRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return piezaTalleService.batchUpsert(idPieza, request, idUsuarioActor);
    }

    @PostMapping("/{idTalle}/revertir")
    public PiezaTalleResponse revertir(
            @PathVariable Long idPieza,
            @PathVariable Long idTalle,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return piezaTalleService.revertir(idPieza, idTalle, idUsuarioActor);
    }
}
