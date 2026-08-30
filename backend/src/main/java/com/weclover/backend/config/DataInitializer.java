package com.weclover.backend.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.entity.Colegio;
import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.Permiso;
import com.weclover.backend.entity.Rol;
import com.weclover.backend.entity.TipoPrenda;
import com.weclover.backend.entity.TipoTela;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.repository.ColegioRepository;
import com.weclover.backend.repository.PaletaColoresRepository;
import com.weclover.backend.repository.PermisoRepository;
import com.weclover.backend.repository.RolRepository;
import com.weclover.backend.repository.TipoPrendaRepository;
import com.weclover.backend.repository.TipoTelaRepository;
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
    private final TipoPrendaRepository tipoPrendaRepository;
    private final TipoTelaRepository tipoTelaRepository;
    private final PaletaColoresRepository paletaColoresRepository;
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

        if (tipoPrendaRepository.count() == 0) {
            List.of("Campera", "Buzo", "Remera", "Chomba", "Bandera")
                .forEach(nombre -> tipoPrendaRepository.save(TipoPrenda.builder().nombre(nombre).build()));
        }

        // Rol nuevo para la carga de imágenes de diseño en Ficha Técnica (ver ProductoService).
        // Se verifica por nombre en vez de por rolRepository.count()==0 porque los demás roles
        // de este entorno (ROLE_ADMINISTRATIVO, ROLE_PLANTA, ROLE_COBRANZAS) ya se crearon a mano
        // por fuera de este seed, así que ese count ya no es 0 acá.
        if (rolRepository.findByNombre("ROLE_DISENADOR").isEmpty()) {
            rolRepository.save(Rol.builder()
                .nombre("ROLE_DISENADOR")
                .descripcion("Diseñador")
                .permisos(new ArrayList<>())
                .build());
        }

        // Catálogo de tipos de tela/insumo (antes era el enum TipoTela): sembrado idempotente
        // por código, para que agregar uno nuevo (ej. Corderito) alguna vez sea solo una fila
        // más acá, no un deploy. "codigo" es la clave estable del contrato de la API (ver
        // TipoTela.java); "nombre" es el humano-legible, todavía sin usar en el front.
        record DefinicionTipoTela(String codigo, String nombre, boolean esPorPeso, boolean telaCuerpo, Integer gramosSugerido) {
        }

        List<DefinicionTipoTela> definicionesTipoTela = List.of(
            new DefinicionTipoTela("FRIZA", "Friza", true, true, null),
            new DefinicionTipoTela("JERSEY", "Jersey", true, true, 70),
            new DefinicionTipoTela("PIQUE", "Piqué", true, true, null),
            new DefinicionTipoTela("SPUM", "Spum", true, true, null),
            new DefinicionTipoTela("RIBB", "Ribb", true, false, 60),
            new DefinicionTipoTela("CIERRE", "Cierre", false, false, null)
        );

        definicionesTipoTela.forEach(def -> {
            if (!tipoTelaRepository.existsByCodigo(def.codigo())) {
                tipoTelaRepository.save(TipoTela.builder()
                    .codigo(def.codigo())
                    .nombre(def.nombre())
                    .esPorPeso(def.esPorPeso())
                    .telaCuerpo(def.telaCuerpo())
                    .gramosSugerido(def.gramosSugerido())
                    .activo(true)
                    .build());
            }
        });

        // Catálogo base de colores x cada tipo de tela (y "cierre"): mismos 10 nombres/hex
        // para cada fila de tipos_tela, insertados de forma idempotente por combinación
        // (nombre, tipoTela) en vez de por count()==0, porque esta tabla ya traía datos
        // reales de antes de agregar esta segmentación (ver doc/tareas-realizadas.md,
        // migraciones manuales de esas fechas).
        Map<String, String> paletaBase = new LinkedHashMap<>();
        paletaBase.put("Marino", "#14213D");
        paletaBase.put("Blanco", "#FFFFFF");
        paletaBase.put("Negro", "#000000");
        paletaBase.put("Verde", "#025939");
        paletaBase.put("Bordó", "#6E1423");
        paletaBase.put("Gris", "#808080");
        paletaBase.put("Beige", "#D8C3A5");
        paletaBase.put("Celeste", "#75AADB");
        paletaBase.put("Amarillo", "#FFD500");
        paletaBase.put("Rojo", "#C8102E");

        for (TipoTela tipoTela : tipoTelaRepository.findAll()) {
            paletaBase.forEach((nombre, hex) -> {
                if (!paletaColoresRepository.existsByNombreIgnoreCaseAndTipoTela(nombre, tipoTela)) {
                    paletaColoresRepository.save(PaletaColores.builder()
                        .nombre(nombre)
                        .hex(hex)
                        .tipoTela(tipoTela)
                        .activo(true)
                        .build());
                }
            });
        }

        System.out.println("--- DATOS SEMILLA CARGADOS CORRECTAMENTE ---");
    }
}
