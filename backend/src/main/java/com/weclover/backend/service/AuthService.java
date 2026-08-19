package com.weclover.backend.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.auth.LoginRequest;
import com.weclover.backend.dto.auth.LoginResponse;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.InvalidCredentialsException;
import com.weclover.backend.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String MENSAJE_CREDENCIALES_INVALIDAS = "Email o contraseña incorrectos";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.email())
            .orElseThrow(() -> new InvalidCredentialsException(MENSAJE_CREDENCIALES_INVALIDAS));

        // No distinguir entre "usuario inexistente", "deshabilitado" o "contraseña incorrecta"
        // en el mensaje de error evita filtrar qué emails están registrados en el sistema.
        if (!usuario.isHabilitado() || !passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new InvalidCredentialsException(MENSAJE_CREDENCIALES_INVALIDAS);
        }

        return new LoginResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol().getNombre());
    }
}
