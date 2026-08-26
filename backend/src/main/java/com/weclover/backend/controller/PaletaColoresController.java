package com.weclover.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.paletacolores.PaletaColorCreateRequest;
import com.weclover.backend.dto.paletacolores.PaletaColorResponse;
import com.weclover.backend.entity.TipoTela;
import com.weclover.backend.service.PaletaColoresService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/paleta-colores")
@RequiredArgsConstructor
public class PaletaColoresController {

    private final PaletaColoresService paletaColoresService;

    @GetMapping
    public List<PaletaColorResponse> listar(@RequestParam(required = false) TipoTela tipoTela) {
        return paletaColoresService.listarActivos(tipoTela);
    }

    @PostMapping
    public ResponseEntity<PaletaColorResponse> crear(
            @Valid @RequestBody PaletaColorCreateRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        PaletaColorResponse response = paletaColoresService.crearColor(request, idUsuarioActor);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
