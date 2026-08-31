package com.weclover.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.stock.StockGuardarCambiosRequest;
import com.weclover.backend.dto.stock.StockResponse;
import com.weclover.backend.service.StockService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @GetMapping
    public List<StockResponse> listar() {
        return stockService.listar();
    }

    @PostMapping("/guardar-cambios")
    public List<StockResponse> guardarCambios(
            @Valid @RequestBody StockGuardarCambiosRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return stockService.guardarCambios(request.items(), idUsuarioActor);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        stockService.eliminar(id, idUsuarioActor);
        return ResponseEntity.noContent().build();
    }
}
