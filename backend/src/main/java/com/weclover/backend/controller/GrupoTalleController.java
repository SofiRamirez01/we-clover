package com.weclover.backend.controller;

import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.grupotalle.GrupoTalleResponse;
import com.weclover.backend.repository.GrupoTalleRepository;

import lombok.RequiredArgsConstructor;

/** Catálogo de solo lectura, mismo criterio que /api/tipos-prenda: sin restricción de rol. */
@RestController
@RequestMapping("/api/grupos-talle")
@RequiredArgsConstructor
public class GrupoTalleController {

    private final GrupoTalleRepository grupoTalleRepository;

    @GetMapping
    public List<GrupoTalleResponse> listar() {
        return grupoTalleRepository.findAll().stream()
            .map(grupo -> new GrupoTalleResponse(grupo.getId(), grupo.getNombre()))
            .sorted(Comparator.comparing(GrupoTalleResponse::nombre))
            .toList();
    }
}
