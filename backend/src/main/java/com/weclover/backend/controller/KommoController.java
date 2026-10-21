package com.weclover.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.kommo.VendedorKommoResponse;
import com.weclover.backend.service.KommoVendedorService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/kommo")
@RequiredArgsConstructor
public class KommoController {

    private final KommoVendedorService kommoVendedorService;

    /** Opciones del campo "Vendedor" de Kommo, en vivo (ver KommoVendedorService). */
    @GetMapping("/vendedores")
    public List<VendedorKommoResponse> listarVendedores(
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return kommoVendedorService.listarVendedores(idUsuarioActor);
    }
}
