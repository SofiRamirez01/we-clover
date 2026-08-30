package com.weclover.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.tipotela.TipoTelaResponse;
import com.weclover.backend.repository.TipoTelaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Catálogo de solo lectura y baja sensibilidad, sin restricción de rol — mismo criterio
 * que GET /api/tipos-prenda.
 */
@RestController
@RequestMapping("/api/tipos-tela")
@RequiredArgsConstructor
public class TipoTelaController {

    private final TipoTelaRepository tipoTelaRepository;

    @GetMapping
    public List<TipoTelaResponse> listar() {
        return tipoTelaRepository.findByActivoTrueOrderByNombreAsc().stream()
            .map(tipo -> new TipoTelaResponse(
                tipo.getId(),
                tipo.getCodigo(),
                tipo.getNombre(),
                tipo.isEsPorPeso(),
                tipo.isTelaCuerpo(),
                tipo.getGramosSugerido()
            ))
            .toList();
    }
}
