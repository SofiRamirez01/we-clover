package com.weclover.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.weclover.backend.dto.usuario.UsuarioCreateRequest;
import com.weclover.backend.dto.usuario.UsuarioUpdateRequest;
import com.weclover.backend.entity.Rol;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.mapper.UsuarioMapper;
import com.weclover.backend.repository.RolRepository;
import com.weclover.backend.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceVendedorKommoTest {

    private static final long ENUM_PIA = 778684L;
    private static final Long ADMIN = 1L;

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private RolRepository rolRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UsuarioMapper usuarioMapper;
    @Mock private AutorizacionService autorizacionService;

    @InjectMocks
    private UsuarioService service;

    private final Rol rolVentas = Rol.builder().id(3L).nombre("ROLE_VENDEDOR").build();
    private final Usuario pia = Usuario.builder().id(5L).nombre("Pia Gómez").email("pia@weclover.com")
        .habilitado(true).kommoVendedorEnumId(ENUM_PIA).build();

    @Test
    void no_se_puede_dar_de_alta_un_usuario_con_un_vendedor_de_kommo_ya_vinculado() {
        when(rolRepository.findById(3L)).thenReturn(Optional.of(rolVentas));
        when(usuarioRepository.findByEmail("beba@weclover.com")).thenReturn(Optional.empty());
        when(usuarioRepository.findByKommoVendedorEnumId(ENUM_PIA)).thenReturn(Optional.of(pia));

        assertThatThrownBy(() -> service.crearUsuario(
                new UsuarioCreateRequest("Beba", "beba@weclover.com", null, 3L, ENUM_PIA), ADMIN))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessage("Ese vendedor de Kommo ya está vinculado al usuario Pia Gómez");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void al_editar_el_mismo_usuario_puede_conservar_su_vendedor() {
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(pia));
        when(rolRepository.findById(3L)).thenReturn(Optional.of(rolVentas));
        when(usuarioRepository.findByEmail("pia@weclover.com")).thenReturn(Optional.of(pia));
        when(usuarioRepository.findByKommoVendedorEnumId(ENUM_PIA)).thenReturn(Optional.of(pia));
        when(usuarioRepository.save(pia)).thenReturn(pia);

        service.actualizarUsuario(5L, new UsuarioUpdateRequest("Pia Gómez", "pia@weclover.com", null, 3L, ENUM_PIA), ADMIN);

        assertThat(pia.getKommoVendedorEnumId()).isEqualTo(ENUM_PIA);
    }

    @Test
    void sin_asignar_quita_el_vinculo() {
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(pia));
        when(rolRepository.findById(3L)).thenReturn(Optional.of(rolVentas));
        when(usuarioRepository.findByEmail("pia@weclover.com")).thenReturn(Optional.of(pia));
        when(usuarioRepository.save(pia)).thenReturn(pia);

        service.actualizarUsuario(5L, new UsuarioUpdateRequest("Pia Gómez", "pia@weclover.com", null, 3L, null), ADMIN);

        assertThat(pia.getKommoVendedorEnumId()).isNull();
        verify(usuarioRepository, never()).findByKommoVendedorEnumId(any());
    }

    @Test
    void deshabilitar_un_usuario_libera_su_vendedor_de_kommo() {
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(pia));

        service.eliminarUsuario(5L, ADMIN);

        assertThat(pia.isHabilitado()).isFalse();
        assertThat(pia.getKommoVendedorEnumId()).isNull();
        verify(usuarioRepository).save(pia);
    }
}
