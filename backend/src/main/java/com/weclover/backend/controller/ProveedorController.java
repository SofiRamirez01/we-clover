package com.weclover.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.proveedor.ArticuloProveedorRequest;
import com.weclover.backend.dto.proveedor.ArticuloProveedorResponse;
import com.weclover.backend.dto.proveedor.CambiarActivoRequest;
import com.weclover.backend.dto.proveedor.ProveedorRequest;
import com.weclover.backend.dto.proveedor.ProveedorResponse;
import com.weclover.backend.service.ArticuloProveedorService;
import com.weclover.backend.service.ProveedorService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/proveedores")
@RequiredArgsConstructor
public class ProveedorController {

    private final ProveedorService proveedorService;
    private final ArticuloProveedorService articuloProveedorService;

    @GetMapping
    public List<ProveedorResponse> listar(
            @RequestParam(required = false) Boolean activo,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return proveedorService.listar(activo, idUsuarioActor);
    }

    @PostMapping
    public ResponseEntity<ProveedorResponse> crear(
            @Valid @RequestBody ProveedorRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        ProveedorResponse response = proveedorService.crear(request, idUsuarioActor);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ProveedorResponse actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ProveedorRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return proveedorService.actualizar(id, request, idUsuarioActor);
    }

    @PatchMapping("/{id}/activo")
    public ProveedorResponse cambiarActivo(
            @PathVariable Long id,
            @Valid @RequestBody CambiarActivoRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return proveedorService.cambiarActivo(id, request.activo(), idUsuarioActor);
    }

    @GetMapping("/{id}/articulos")
    public List<ArticuloProveedorResponse> listarArticulos(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return articuloProveedorService.listarPorProveedor(id, idUsuarioActor);
    }

    @PostMapping("/{id}/articulos")
    public ResponseEntity<ArticuloProveedorResponse> agregarArticulo(
            @PathVariable Long id,
            @Valid @RequestBody ArticuloProveedorRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        ArticuloProveedorResponse response = articuloProveedorService.agregarArticulo(id, request, idUsuarioActor);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
