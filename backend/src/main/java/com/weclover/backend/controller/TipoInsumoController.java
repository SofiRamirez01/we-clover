package com.weclover.backend.controller;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.tipoinsumo.TipoInsumoResponse;
import com.weclover.backend.repository.TipoInsumoRepository;
import com.weclover.backend.service.TipoInsumoCodigos;

import lombok.RequiredArgsConstructor;

/**
 * Catálogo de solo lectura y baja sensibilidad, sin restricción de rol — mismo criterio
 * que GET /api/tipos-tela. Solo los tipos activos: uno inactivo sigue valiendo en las fichas y
 * piezas que ya lo usan, pero deja de ofrecerse.
 */
@RestController
@RequestMapping("/api/tipos-insumo")
@RequiredArgsConstructor
public class TipoInsumoController {

    private final TipoInsumoRepository tipoInsumoRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public List<TipoInsumoResponse> listar() {
        return tipoInsumoRepository.findByActivoTrueOrderByNombreAsc().stream()
            .map(tipo -> new TipoInsumoResponse(
                tipo.getId(),
                tipo.getCodigo(),
                tipo.getNombre(),
                tipo.isConsumeMaterial(),
                tipo.getTipoTelaSugerido() != null ? tipo.getTipoTelaSugerido().getId() : null,
                tipo.getTipoTelaSugerido() != null ? tipo.getTipoTelaSugerido().getCodigo() : null,
                TipoInsumoCodigos.soloTelasPorPeso(tipo)
            ))
            .toList();
    }
}
