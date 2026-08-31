package com.weclover.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.planificacioncompra.ArticuloResumenResponse;
import com.weclover.backend.dto.planificacioncompra.PlanificacionCompraBorradorRequest;
import com.weclover.backend.dto.planificacioncompra.PlanificacionCompraBorradorResponse;
import com.weclover.backend.dto.planificacioncompra.PlanificacionCompraDetalleResponse;
import com.weclover.backend.dto.planificacioncompra.PlanificacionCompraResponse;
import com.weclover.backend.service.PlanificacionCompraService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/planificaciones-compra")
@RequiredArgsConstructor
public class PlanificacionCompraController {

    private final PlanificacionCompraService planificacionCompraService;

    @GetMapping
    public List<PlanificacionCompraResponse> listar(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return planificacionCompraService.listar(idUsuarioActor);
    }

    @GetMapping("/{id}")
    public PlanificacionCompraResponse obtener(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return planificacionCompraService.obtenerCabecera(id, idUsuarioActor);
    }

    @PostMapping("/borradores")
    public ResponseEntity<PlanificacionCompraResponse> crearBorrador(
            @RequestBody PlanificacionCompraBorradorRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        PlanificacionCompraResponse response = planificacionCompraService.guardarBorrador(null, request, idUsuarioActor);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/borradores/{id}")
    public PlanificacionCompraResponse actualizarBorrador(
            @PathVariable Long id,
            @RequestBody PlanificacionCompraBorradorRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return planificacionCompraService.guardarBorrador(id, request, idUsuarioActor);
    }

    @GetMapping("/{id}/borrador")
    public PlanificacionCompraBorradorResponse obtenerBorrador(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return planificacionCompraService.obtenerBorrador(id, idUsuarioActor);
    }

    @PostMapping("/{id}/confirmar")
    public PlanificacionCompraResponse confirmar(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return planificacionCompraService.confirmar(id, idUsuarioActor);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        planificacionCompraService.eliminarBorrador(id, idUsuarioActor);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/resumen")
    public List<ArticuloResumenResponse> resumen(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return planificacionCompraService.obtenerResumen(id, idUsuarioActor);
    }

    @GetMapping("/{id}/detalle")
    public List<PlanificacionCompraDetalleResponse> detalle(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return planificacionCompraService.obtenerDetallePorProducto(id, idUsuarioActor);
    }
}
