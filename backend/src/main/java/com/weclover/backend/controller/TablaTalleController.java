package com.weclover.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.tablatalle.TablaTalleResponse;
import com.weclover.backend.entity.GrupoTalle;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.repository.GrupoTalleRepository;
import com.weclover.backend.repository.TablaTalleRepository;

import lombok.RequiredArgsConstructor;

/** Catálogo de solo lectura, mismo criterio que /api/tipos-prenda: sin restricción de rol. */
@RestController
@RequestMapping("/api/tablas-talle")
@RequiredArgsConstructor
public class TablaTalleController {

    private final TablaTalleRepository tablaTalleRepository;
    private final GrupoTalleRepository grupoTalleRepository;

    @GetMapping
    public List<TablaTalleResponse> listar(@RequestParam Long idGrupoTalle) {
        GrupoTalle grupoTalle = grupoTalleRepository.findById(idGrupoTalle)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el grupo de talle con id " + idGrupoTalle));

        return tablaTalleRepository.findByGrupoTalleOrderByOrdenAsc(grupoTalle).stream()
            .map(tabla -> new TablaTalleResponse(tabla.getId(), tabla.getTalle(), tabla.getAnchoCm(), tabla.getLargoCm()))
            .toList();
    }
}
