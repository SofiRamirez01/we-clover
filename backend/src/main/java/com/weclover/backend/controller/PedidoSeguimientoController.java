package com.weclover.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.pedido.NotaPedidoRequest;
import com.weclover.backend.dto.pedido.NotaPedidoResponse;
import com.weclover.backend.dto.pedido.UbicacionPedidoRequest;
import com.weclover.backend.dto.pedido.UbicacionPedidoResponse;
import com.weclover.backend.service.PedidoSeguimientoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Notas y ubicación de un pedido (seguimiento interno de producción). */
@RestController
@RequestMapping("/api/pedidos/{id}")
@RequiredArgsConstructor
public class PedidoSeguimientoController {

    private final PedidoSeguimientoService pedidoSeguimientoService;

    @GetMapping("/notas")
    public List<NotaPedidoResponse> listarNotas(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return pedidoSeguimientoService.listarNotas(id, idUsuarioActor);
    }

    @PostMapping("/notas")
    public ResponseEntity<NotaPedidoResponse> agregarNota(
            @PathVariable Long id,
            @Valid @RequestBody NotaPedidoRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        NotaPedidoResponse response = pedidoSeguimientoService.agregarNota(id, request.texto(), idUsuarioActor);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/ubicacion")
    public UbicacionPedidoResponse cambiarUbicacion(
            @PathVariable Long id,
            @Valid @RequestBody UbicacionPedidoRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return pedidoSeguimientoService.cambiarUbicacion(id, request.ubicacion(), idUsuarioActor);
    }
}
