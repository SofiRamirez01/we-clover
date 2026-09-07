package com.weclover.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.cargatalles.CargaTallesResponse;
import com.weclover.backend.dto.cargatalles.LinkCargaTallesResponse;
import com.weclover.backend.service.CargaTallesService;

import lombok.RequiredArgsConstructor;

/** Acciones internas sobre la carga de talles de un Pedido puntual — el link se genera solo al
 *  crear el pedido (ver PedidoService.crearPedido), así que acá solo queda consultarlo (Ficha
 *  Técnica, visible para cualquier rol) y cerrar/reabrir. Ver CargaTallesPublicaController para
 *  las rutas sin login que usa el representante de curso. */
@RestController
@RequestMapping("/api/pedidos/{idPedido}/carga-talles")
@RequiredArgsConstructor
public class PedidoCargaTallesController {

    private final CargaTallesService cargaTallesService;

    @GetMapping
    public CargaTallesResponse obtener(@PathVariable Long idPedido) {
        return cargaTallesService.obtenerInterno(idPedido);
    }

    @PostMapping("/cerrar")
    public LinkCargaTallesResponse cerrar(
            @PathVariable Long idPedido,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return cargaTallesService.cerrar(idPedido, idUsuarioActor);
    }

    @PostMapping("/reabrir")
    public LinkCargaTallesResponse reabrir(
            @PathVariable Long idPedido,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return cargaTallesService.reabrir(idPedido, idUsuarioActor);
    }
}
