package com.weclover.backend.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
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

import com.weclover.backend.dto.planificacioncompra.ProductoElegibleResponse;
import com.weclover.backend.dto.producto.ActualizarColorCierreRequest;
import com.weclover.backend.dto.producto.ActualizarPatronCorteRequest;
import com.weclover.backend.dto.producto.ActualizarTipoTelaRequest;
import com.weclover.backend.dto.producto.MarcarEstadoBanderaRequest;
import com.weclover.backend.dto.producto.MarcarEtapaRequest;
import com.weclover.backend.dto.producto.MarcarEtapasBulkRequest;
import com.weclover.backend.dto.producto.ProductoColoresRequest;
import com.weclover.backend.dto.producto.ProductoEtapasResponse;
import com.weclover.backend.dto.producto.ProductoInsumosSecundariosRequest;
import com.weclover.backend.dto.producto.ProductoResponse;
import com.weclover.backend.entity.EtapaProduccion;
import com.weclover.backend.service.PlanificacionCompraService;
import com.weclover.backend.service.ProductoEtapaProduccionService;
import com.weclover.backend.service.ProductoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;
    private final PlanificacionCompraService planificacionCompraService;
    private final ProductoEtapaProduccionService productoEtapaProduccionService;

    /** Productos candidatos a una PlanificacionCompra (Fase 3) — filtra por
     *  Pedido.fechaEstimadaEntrega, no por fecha de venta. Ver PlanificacionCompraService. */
    @GetMapping("/elegibles-planificacion")
    public List<ProductoElegibleResponse> listarElegiblesPlanificacion(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) Long idColegio,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return planificacionCompraService.listarProductosElegibles(fechaDesde, fechaHasta, idColegio, idUsuarioActor);
    }

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

    @PostMapping("/{id}/colores")
    public ProductoResponse asignarColores(
            @PathVariable Long id,
            @Valid @RequestBody ProductoColoresRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return productoService.asignarColores(id, request, idUsuarioActor);
    }

    @PatchMapping("/{id}/patron-corte")
    public ProductoResponse actualizarPatronCorte(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarPatronCorteRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return productoService.actualizarPatronCorte(id, request, idUsuarioActor);
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

    @PutMapping("/{id}/insumos-secundarios")
    public ProductoResponse actualizarInsumosSecundarios(
            @PathVariable Long id,
            @Valid @RequestBody ProductoInsumosSecundariosRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return productoService.actualizarInsumosSecundarios(id, request, idUsuarioActor);
    }

    @PutMapping("/{id}/bandera/estado")
    public ProductoResponse marcarEstadoBandera(
            @PathVariable Long id,
            @Valid @RequestBody MarcarEstadoBanderaRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return productoService.marcarEstadoBandera(id, request, idUsuarioActor);
    }

    @GetMapping("/{id}/etapas")
    public ProductoEtapasResponse listarEtapas(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return productoEtapaProduccionService.listarEtapas(id, idUsuarioActor);
    }

    @PutMapping("/{id}/etapas/{etapa}")
    public ProductoEtapasResponse marcarEtapa(
            @PathVariable Long id,
            @PathVariable EtapaProduccion etapa,
            @Valid @RequestBody MarcarEtapaRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return productoEtapaProduccionService.marcarEtapa(id, etapa, request.completado(), request.idEmpleado(), idUsuarioActor);
    }

    @PutMapping("/etapas/bulk")
    public void marcarEtapasBulk(
            @Valid @RequestBody MarcarEtapasBulkRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        productoEtapaProduccionService.marcarEtapasBulk(request.etapas(), idUsuarioActor);
    }
}
