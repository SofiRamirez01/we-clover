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

import com.weclover.backend.dto.usuario.UsuarioCreateRequest;
import com.weclover.backend.dto.usuario.UsuarioResponse;
import com.weclover.backend.dto.usuario.UsuarioUpdateRequest;
import com.weclover.backend.service.UsuarioService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @PostMapping
    public ResponseEntity<UsuarioResponse> crearUsuario(@Valid @RequestBody UsuarioCreateRequest request) {
        UsuarioResponse response = usuarioService.crearUsuario(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/corporativos")
    public List<UsuarioResponse> listarUsuariosCorporativos() {
        return usuarioService.listarUsuariosCorporativos();
    }

    @PutMapping("/{id}")
    public UsuarioResponse actualizarUsuario(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioUpdateRequest request,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        return usuarioService.actualizarUsuario(id, request, idUsuarioActor);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarUsuario(
            @PathVariable Long id,
            @RequestHeader(value = "X-Usuario-Id", required = false) Long idUsuarioActor) {
        usuarioService.eliminarUsuario(id, idUsuarioActor);
        return ResponseEntity.noContent().build();
    }
}
