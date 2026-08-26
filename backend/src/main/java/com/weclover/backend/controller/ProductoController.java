package com.weclover.backend.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.weclover.backend.dto.producto.ActualizarColorCierreRequest;
import com.weclover.backend.dto.producto.ActualizarTipoTelaRequest;
import com.weclover.backend.dto.producto.CambioEstadoProductoRequest;
import com.weclover.backend.dto.producto.ProductoColoresRequest;
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

    @DeleteMapping("/{id}/imagen")
    public ProductoResponse eliminarImagenDiseno(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return productoService.eliminarImagenDiseno(id, idUsuarioActor);
    }

    @PatchMapping("/{id}/estado")
    public ProductoResponse cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody CambioEstadoProductoRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return productoService.cambiarEstado(id, request, idUsuarioActor);
    }

    @PostMapping("/{id}/colores")
    public ProductoResponse asignarColores(
            @PathVariable Long id,
            @Valid @RequestBody ProductoColoresRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return productoService.asignarColores(id, request, idUsuarioActor);
    }

    @PatchMapping("/{id}/tipo-tela")
    public ProductoResponse actualizarTipoTela(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarTipoTelaRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return productoService.actualizarTipoTela(id, request, idUsuarioActor);
    }

    @PatchMapping("/{id}/color-cierre")
    public ProductoResponse actualizarColorCierre(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarColorCierreRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return productoService.actualizarColorCierre(id, request, idUsuarioActor);
    }
}
