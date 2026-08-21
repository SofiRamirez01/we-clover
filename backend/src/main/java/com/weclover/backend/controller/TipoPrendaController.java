package com.weclover.backend.controller;

import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.tipoprenda.TipoPrendaResponse;
import com.weclover.backend.repository.TipoPrendaRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tipos-prenda")
@RequiredArgsConstructor
public class TipoPrendaController {

    private final TipoPrendaRepository tipoPrendaRepository;

    @GetMapping
    public List<TipoPrendaResponse> listar() {
        return tipoPrendaRepository.findAll().stream()
            .map(tipo -> new TipoPrendaResponse(tipo.getId(), tipo.getNombre()))
            .sorted(Comparator.comparing(TipoPrendaResponse::nombre))
            .toList();
    }
}
