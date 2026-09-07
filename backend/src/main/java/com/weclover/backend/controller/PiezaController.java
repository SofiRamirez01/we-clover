package com.weclover.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.pieza.CalcularBasePreviewRequest;
import com.weclover.backend.dto.pieza.PiezaCreateRequest;
import com.weclover.backend.dto.pieza.PiezaDetalleResponse;
import com.weclover.backend.dto.pieza.PiezaGeometriaCalculoResponse;
import com.weclover.backend.dto.pieza.PiezaResponse;
import com.weclover.backend.dto.pieza.PiezaUpdateRequest;
import com.weclover.backend.service.PiezaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/piezas")
@RequiredArgsConstructor
public class PiezaController {

    private final PiezaService piezaService;

    @PostMapping
    public ResponseEntity<PiezaResponse> crear(
            @Valid @RequestBody PiezaCreateRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        PiezaResponse response = piezaService.crear(
            request.nombre(),
            request.idGrupoTalle(),
            request.idTalleBase(),
            request.segmentos(),
            request.simetrica(),
            idUsuarioActor);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Proxy de solo cálculo hacia el servicio de geometría, para la vista previa del editor de segmentos. */
    @PostMapping("/calcular-base")
    public PiezaGeometriaCalculoResponse calcularBase(
            @Valid @RequestBody CalcularBasePreviewRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return piezaService.calcularBasePreview(request.segmentos(), request.simetrica(), idUsuarioActor);
    }

    @GetMapping
    public List<PiezaResponse> listar(
            @RequestParam(required = false) Long idGrupoTalle,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return piezaService.listarActivas(idGrupoTalle, idUsuarioActor);
    }

    /** Detalle completo (con los segmentos originales), para reabrir el editor al editar o duplicar. */
    @GetMapping("/{id}")
    public PiezaDetalleResponse obtener(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return piezaService.obtenerDetalle(id, idUsuarioActor);
    }

    @PutMapping("/{id}")
    public PiezaResponse actualizar(
            @PathVariable Long id,
            @Valid @RequestBody PiezaUpdateRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return piezaService.actualizar(
            id,
            request.nombre(),
            request.idGrupoTalle(),
            request.idTalleBase(),
            request.segmentos(),
            request.simetrica(),
            idUsuarioActor);
    }
}
