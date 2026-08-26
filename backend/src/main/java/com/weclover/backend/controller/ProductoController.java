package com.weclover.backend.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.weclover.backend.dto.producto.CambioEstadoProductoRequest;
import com.weclover.backend.dto.producto.ProductoResponse;
import com.weclover.backend.service.ProductoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;

    @PostMapping(value = "/{id}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductoResponse subirImagenDiseno(
            @PathVariable Long id,
            @RequestParam("imagen") MultipartFile imagen,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return productoService.subirImagenDiseno(id, imagen, idUsuarioActor);
    }

    @PatchMapping("/{id}/estado")
    public ProductoResponse cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody CambioEstadoProductoRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return productoService.cambiarEstado(id, request, idUsuarioActor);
    }
}
