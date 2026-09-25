package com.weclover.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.produccion.ProduccionPedidoResponse;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.EtapaProduccion;
import com.weclover.backend.service.ProduccionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/produccion")
@RequiredArgsConstructor
public class ProduccionController {

    private final ProduccionService produccionService;

    @GetMapping("/pedidos")
    public List<ProduccionPedidoResponse> listarPedidos(
            @RequestParam(required = false) List<EstadoPedido> estado,
            @RequestParam(required = false) EtapaProduccion etapaPendiente,
            @RequestParam(required = false) Float pagoMin,
            @RequestParam(required = false) Float pagoMax,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return produccionService.listar(estado, etapaPendiente, pagoMin, pagoMax, idUsuarioActor);
    }
}
