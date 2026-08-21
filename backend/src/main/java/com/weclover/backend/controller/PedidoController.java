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

import com.weclover.backend.dto.pedido.CambioEstadoRequest;
import com.weclover.backend.dto.pedido.PedidoCreateRequest;
import com.weclover.backend.dto.pedido.PedidoResponse;
import com.weclover.backend.service.PedidoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;

    @PostMapping
    public ResponseEntity<PedidoResponse> crearPedido(@Valid @RequestBody PedidoCreateRequest request) {
        PedidoResponse response = pedidoService.crearPedido(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<PedidoResponse> listarPedidos() {
        return pedidoService.listarPedidos();
    }

    @PatchMapping("/{id}/estado")
    public PedidoResponse cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody CambioEstadoRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return pedidoService.cambiarEstado(id, request, idUsuarioActor);
    }
}
