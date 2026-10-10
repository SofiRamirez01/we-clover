package com.weclover.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.tanda.AlertaPriorizacionResponse;
import com.weclover.backend.dto.tanda.PriorizacionRequest;
import com.weclover.backend.dto.tanda.PriorizacionResponse;
import com.weclover.backend.dto.tanda.TandaRenombrarRequest;
import com.weclover.backend.dto.tanda.TandaResponse;
import com.weclover.backend.service.PriorizacionTandaService;
import com.weclover.backend.service.TandaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tandas")
@RequiredArgsConstructor
public class TandaController {

    private final TandaService tandaService;
    private final PriorizacionTandaService priorizacionTandaService;

    /** Por defecto solo las tandas no cerradas, en orden de cola. */
    @GetMapping
    public List<TandaResponse> listar(
            @RequestParam(defaultValue = "false") boolean incluirCerradas,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return tandaService.listar(incluirCerradas, idUsuarioActor);
    }

    @GetMapping("/alertas")
    public List<AlertaPriorizacionResponse> listarAlertas(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return tandaService.listarAlertas(idUsuarioActor);
    }

    /** Aplica en una sola transacción todo lo que cambió el popup de priorización (tandas
     *  nuevas, orden de la cola y movimientos de pedidos): se guarda todo o nada. */
    @PostMapping("/priorizacion")
    public PriorizacionResponse priorizar(
            @Valid @RequestBody PriorizacionRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return priorizacionTandaService.aplicar(request, idUsuarioActor);
    }

    @PatchMapping("/{id}")
    public TandaResponse renombrar(
            @PathVariable Long id,
            @Valid @RequestBody TandaRenombrarRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return tandaService.renombrar(id, request.nombre(), idUsuarioActor);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        tandaService.eliminar(id, idUsuarioActor);
        return ResponseEntity.noContent().build();
    }
}
