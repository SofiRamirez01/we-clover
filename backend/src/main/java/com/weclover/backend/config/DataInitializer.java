package com.weclover.backend.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.entity.Colegio;
import com.weclover.backend.entity.Permiso;
import com.weclover.backend.entity.Rol;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.repository.ColegioRepository;
import com.weclover.backend.repository.PermisoRepository;
import com.weclover.backend.repository.RolRepository;
import com.weclover.backend.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final String PERMISO_PEDIDO_CREAR = "PEDIDO_CREAR";
    private static final String PERMISO_PEDIDO_LEER = "PEDIDO_LEER";
    private static final String PERMISO_PEDIDO_ACTUALIZAR_ESTADO = "PEDIDO_ACTUALIZAR_ESTADO";

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ColegioRepository colegioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (rolRepository.count() == 0 || usuarioRepository.count() == 0) {
            Permiso permisoCrear = permisoRepository.save(Permiso.builder()
                .nombre(PERMISO_PEDIDO_CREAR)
                .descripcion("Permite crear pedidos")
                .build());
            Permiso permisoLeer = permisoRepository.save(Permiso.builder()
                .nombre(PERMISO_PEDIDO_LEER)
                .descripcion("Permite consultar pedidos")
                .build());
            Permiso permisoActualizarEstado = permisoRepository.save(Permiso.builder()
                .nombre(PERMISO_PEDIDO_ACTUALIZAR_ESTADO)
                .descripcion("Permite actualizar el estado de un pedido")
                .build());

            Rol rolVendedor = rolRepository.save(Rol.builder()
                .nombre("ROLE_VENDEDOR")
                .descripcion("Vendedor")
                .permisos(new ArrayList<>(List.of(permisoCrear, permisoLeer, permisoActualizarEstado)))
                .build());

            rolRepository.save(Rol.builder()
                .nombre("ROLE_CLIENTE")
                .descripcion("Cliente / Representante de Curso")
                .permisos(new ArrayList<>(List.of(permisoLeer)))
                .build());

            usuarioRepository.save(Usuario.builder()
                .rol(rolVendedor)
                .nombre("Vendedor de Prueba")
                .email("vendedor.prueba@weclover.com")
                .telefono("+54 11 5555-5555")
                .passwordHash(passwordEncoder.encode("Vendedor123!"))
                .habilitado(true)
                .build());

            colegioRepository.save(Colegio.builder()
                .nombre("Colegio de Prueba")
                .provincia("Buenos Aires")
                .localidad("La Plata")
                .build());
        }

        System.out.println("--- DATOS SEMILLA CARGADOS CORRECTAMENTE ---");
    }
}
