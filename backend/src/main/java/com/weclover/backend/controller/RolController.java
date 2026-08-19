package com.weclover.backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.rol.RolResponse;
import com.weclover.backend.service.UsuarioService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RolController {

    private final UsuarioService usuarioService;

    @GetMapping("/corporativos")
    public List<RolResponse> listarRolesCorporativos() {
        return usuarioService.listarRolesCorporativos();
    }
}
