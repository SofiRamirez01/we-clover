package com.weclover.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import org.springframework.web.multipart.MultipartFile;

import com.weclover.backend.dto.pedido.CambioEstadoRequest;
import com.weclover.backend.dto.pedido.HistorialCambioResponse;
import com.weclover.backend.dto.pedido.ImportacionPedidosExcelResponse;
import com.weclover.backend.dto.pedido.PedidoCreateRequest;
import com.weclover.backend.dto.pedido.PedidoResponse;
import com.weclover.backend.dto.pedido.PedidoUpdateRequest;
import com.weclover.backend.dto.pedido.PrioridadManualRequest;
import com.weclover.backend.service.PedidoImportService;
import com.weclover.backend.service.PedidoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;
    private final PedidoImportService pedidoImportService;

    @PostMapping
    public ResponseEntity<PedidoResponse> crearPedido(@Valid @RequestBody PedidoCreateRequest request) {
        PedidoResponse response = pedidoService.crearPedido(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<PedidoResponse> listarPedidos() {
        return pedidoService.listarPedidos();
    }

    @GetMapping("/{id}")
    public PedidoResponse obtenerPedido(@PathVariable Long id) {
        return pedidoService.obtenerPedido(id);
    }

    @PutMapping("/{id}")
    public PedidoResponse actualizarPedido(
            @PathVariable Long id,
            @Valid @RequestBody PedidoUpdateRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return pedidoService.actualizarPedido(id, request, idUsuarioActor);
    }

    @PatchMapping("/{id}/estado")
    public PedidoResponse cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody CambioEstadoRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return pedidoService.cambiarEstado(id, request, idUsuarioActor);
    }

    @GetMapping("/{id}/historial")
    public List<HistorialCambioResponse> obtenerHistorial(@PathVariable Long id) {
        return pedidoService.listarHistorial(id);
    }

    @PostMapping("/importar-excel")
    public ImportacionPedidosExcelResponse importarExcel(@RequestParam("archivo") MultipartFile archivo) {
        return pedidoImportService.importarDesdeExcel(archivo);
    }

    @PutMapping("/{id}/prioridad")
    public PedidoResponse asignarPrioridadManual(
            @PathVariable Long id,
            @Valid @RequestBody PrioridadManualRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return pedidoService.asignarPrioridadManual(id, request.prioridad(), idUsuarioActor);
    }

    @DeleteMapping("/{id}/prioridad")
    public PedidoResponse quitarPrioridadManual(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return pedidoService.quitarPrioridadManual(id, idUsuarioActor);
    }
}
