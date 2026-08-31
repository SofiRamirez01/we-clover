package com.weclover.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.proveedor.ArticuloProveedorResponse;
import com.weclover.backend.dto.proveedor.ArticuloProveedorUpdateRequest;
import com.weclover.backend.service.ArticuloProveedorService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Consultas/edición de un ArticuloProveedor puntual "por color", separadas de
 * ProveedorController porque no dependen de un id de proveedor (ver panel de detalle de color
 * en la pantalla Carta de colores).
 */
@RestController
@RequestMapping("/api/articulos-proveedor")
@RequiredArgsConstructor
public class ArticuloProveedorController {

    private final ArticuloProveedorService articuloProveedorService;

    @GetMapping
    public List<ArticuloProveedorResponse> listarPorColor(
            @RequestParam Long idPaletaColor,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return articuloProveedorService.listarPorPaletaColor(idPaletaColor, idUsuarioActor);
    }

    @PutMapping("/{id}")
    public ArticuloProveedorResponse actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ArticuloProveedorUpdateRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return articuloProveedorService.actualizarArticulo(id, request, idUsuarioActor);
    }
}
