package com.weclover.backend.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.weclover.backend.dto.pedido.CambioEstadoRequest;
import com.weclover.backend.dto.pedido.CampoOrdenPedidos;
import com.weclover.backend.dto.pedido.FiltrosListadoPedidos;
import com.weclover.backend.dto.pedido.HistorialCambioResponse;
import com.weclover.backend.dto.pedido.ImportacionPedidosExcelResponse;
import com.weclover.backend.dto.pedido.ListadoPedidosResponse;
import com.weclover.backend.dto.pedido.OpcionesFiltroPedidosResponse;
import com.weclover.backend.dto.pedido.PedidoCreateRequest;
import com.weclover.backend.dto.pedido.PedidoResponse;
import com.weclover.backend.dto.pedido.PedidoUpdateRequest;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.service.PedidoImportService;
import com.weclover.backend.service.PedidoListadoService;
import com.weclover.backend.service.PedidoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;
    private final PedidoImportService pedidoImportService;
    private final PedidoListadoService pedidoListadoService;

    @PostMapping
    public ResponseEntity<PedidoResponse> crearPedido(@Valid @RequestBody PedidoCreateRequest request) {
        PedidoResponse response = pedidoService.crearPedido(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<PedidoResponse> listarPedidos() {
        return pedidoService.listarPedidos();
    }

    /** Listado filtrado y paginado de Base Ventas (pestañas Pedidos e Histórico, ver
     *  PedidoListadoService). GET /api/pedidos sigue devolviendo todo (lo usa Fichas Técnicas). */
    @GetMapping("/listado")
    public ListadoPedidosResponse listarPaginado(
            @RequestParam(required = false) String buscar,
            @RequestParam(required = false) Integer promocion,
            @RequestParam(required = false) EstadoPedido estado,
            @RequestParam(required = false) Boolean soloActivos,
            @RequestParam(required = false) Long idPatronCorte,
            @RequestParam(required = false) Long idTipoPrenda,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanio,
            @RequestParam(required = false) CampoOrdenPedidos orden,
            @RequestParam(required = false) Sort.Direction direccion,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        FiltrosListadoPedidos filtros = new FiltrosListadoPedidos(
            buscar, promocion, estado, soloActivos, idPatronCorte, idTipoPrenda, desde, hasta, pagina, tamanio,
            orden, direccion);
        return pedidoListadoService.listar(filtros, idUsuarioActor);
    }

    /** Promociones y molderías – tipo de prenda que aparecen en los pedidos visibles. */
    @GetMapping("/opciones-filtro")
    public OpcionesFiltroPedidosResponse opcionesFiltro(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return pedidoListadoService.opcionesFiltro(idUsuarioActor);
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
    public List<HistorialCambioResponse> obtenerHistorial(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return pedidoService.listarHistorial(id, idUsuarioActor);
    }

    @PostMapping("/importar-excel")
    public ImportacionPedidosExcelResponse importarExcel(@RequestParam("archivo") MultipartFile archivo) {
        return pedidoImportService.importarDesdeExcel(archivo);
    }

    /** La prioridad numérica por pedido fue reemplazada por las tandas (ver TandaController):
     *  el recurso ya no existe, se responde 410 Gone a cualquier cliente viejo. */
    @Deprecated
    @RequestMapping(value = "/{id}/prioridad", method = { RequestMethod.PUT, RequestMethod.DELETE })
    public ResponseEntity<Map<String, Object>> prioridadManualEliminada() {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.GONE.value());
        body.put("mensaje", "La prioridad manual por pedido fue reemplazada por las tandas de producción");
        return ResponseEntity.status(HttpStatus.GONE).body(body);
    }
}
