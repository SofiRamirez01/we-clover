package com.weclover.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.patroncorte.PatronCorteCreateRequest;
import com.weclover.backend.dto.patroncorte.PatronCorteResponse;
import com.weclover.backend.service.PatronCorteService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/patrones-corte")
@RequiredArgsConstructor
public class PatronCorteController {

    private final PatronCorteService patronCorteService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PatronCorteResponse> crear(
            @Valid @ModelAttribute PatronCorteCreateRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        PatronCorteResponse response = patronCorteService.crearPatronCorte(request, idUsuarioActor);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<PatronCorteResponse> listar(
            @RequestParam(required = false) Long idTipoPrenda,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return patronCorteService.listarActivos(idTipoPrenda, idUsuarioActor);
    }

    @GetMapping("/{id}")
    public PatronCorteResponse obtener(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return patronCorteService.obtenerPatronCorte(id, idUsuarioActor);
    }
}
