package com.weclover.backend.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.pieza.PiezaGeometriaCalculoResponse;
import com.weclover.backend.dto.pieza.SegmentoDto;
import com.weclover.backend.entity.Colegio;
import com.weclover.backend.entity.GrupoTalle;
import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.Permiso;
import com.weclover.backend.entity.Pieza;
import com.weclover.backend.entity.PiezaTalle;
import com.weclover.backend.entity.Rol;
import com.weclover.backend.entity.TablaTalle;
import com.weclover.backend.entity.TipoPrenda;
import com.weclover.backend.entity.TipoTela;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.repository.ColegioRepository;
import com.weclover.backend.repository.GrupoTalleRepository;
import com.weclover.backend.repository.PaletaColoresRepository;
import com.weclover.backend.repository.PermisoRepository;
import com.weclover.backend.repository.PiezaRepository;
import com.weclover.backend.repository.PiezaTalleRepository;
import com.weclover.backend.repository.RolRepository;
import com.weclover.backend.repository.TablaTalleRepository;
import com.weclover.backend.repository.TipoPrendaRepository;
import com.weclover.backend.repository.TipoTelaRepository;
import com.weclover.backend.repository.UsuarioRepository;
import com.weclover.backend.service.PiezaGeometriaClient;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

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
    private final GrupoTalleRepository grupoTalleRepository;
    private final TablaTalleRepository tablaTalleRepository;
    private final PiezaRepository piezaRepository;
    private final PiezaTalleRepository piezaTalleRepository;
    private final PiezaGeometriaClient piezaGeometriaClient;
    private final ObjectMapper objectMapper;
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

        // Catálogo de grupos de talles (Carga de Talles) — mismo criterio idempotente que
        // TipoTela: por nombre/combinación, no por count()==0, para poder agregar un grupo o
        // una fila nueva más adelante sin volver a correr todo el seed desde cero.
        record DefinicionFilaTalle(String talle, int orden, int anchoCm, int largoCm) {
        }

        Map<String, List<DefinicionFilaTalle>> definicionesGrupoTalle = new LinkedHashMap<>();
        definicionesGrupoTalle.put("Campera/Buzo", List.of(
            new DefinicionFilaTalle("1", 1, 46, 60),
            new DefinicionFilaTalle("2", 2, 48, 60),
            new DefinicionFilaTalle("3", 3, 52, 62),
            new DefinicionFilaTalle("4", 4, 54, 66),
            new DefinicionFilaTalle("5", 5, 56, 68),
            new DefinicionFilaTalle("6", 6, 59, 70),
            new DefinicionFilaTalle("7", 7, 66, 74)
        ));
        definicionesGrupoTalle.put("Chomba/Remera", List.of(
            new DefinicionFilaTalle("12", 1, 41, 56),
            new DefinicionFilaTalle("14", 2, 44, 58),
            new DefinicionFilaTalle("16", 3, 46, 61),
            new DefinicionFilaTalle("18", 4, 49, 65),
            new DefinicionFilaTalle("20", 5, 52, 67),
            new DefinicionFilaTalle("22", 6, 55, 70),
            new DefinicionFilaTalle("24", 7, 60, 74)
        ));

        definicionesGrupoTalle.forEach((nombreGrupo, filas) -> {
            GrupoTalle grupo = grupoTalleRepository.findByNombre(nombreGrupo)
                .orElseGet(() -> grupoTalleRepository.save(GrupoTalle.builder().nombre(nombreGrupo).build()));
            filas.forEach(fila -> {
                if (!tablaTalleRepository.existsByGrupoTalleAndOrden(grupo, fila.orden())) {
                    tablaTalleRepository.save(TablaTalle.builder()
                        .grupoTalle(grupo)
                        .talle(fila.talle())
                        .orden(fila.orden())
                        .anchoCm(fila.anchoCm())
                        .largoCm(fila.largoCm())
                        .build());
                }
            });
        });

        // Asigna el grupo de talles a cada TipoPrenda existente (idempotente: si ya lo tiene
        // asignado no lo vuelve a tocar). Bandera queda sin asignar a propósito — no usa talles.
        Map<String, String> grupoTallePorTipoPrenda = Map.of(
            "Buzo", "Campera/Buzo",
            "Campera", "Campera/Buzo",
            "Remera", "Chomba/Remera",
            "Chomba", "Chomba/Remera"
        );
        grupoTallePorTipoPrenda.forEach((nombreTipoPrenda, nombreGrupo) ->
            tipoPrendaRepository.findByNombre(nombreTipoPrenda).ifPresent(tipoPrenda -> {
                if (tipoPrenda.getGrupoTalle() == null) {
                    grupoTalleRepository.findByNombre(nombreGrupo).ifPresent(grupo -> {
                        tipoPrenda.setGrupoTalle(grupo);
                        tipoPrendaRepository.save(tipoPrenda);
                    });
                }
            }));

        // Migración manual (Requisito 4.1 Parte 3, CAMBIO 1): backfill de PiezaTalle esBase=true
        // para las Piezas que ya existían antes de que la geometría base se moviera de Pieza a
        // PiezaTalle. No se tocan ni se leen las columnas viejas de piezas (coordenadas_base_json/
        // ancho_base_cm/largo_base_cm): se recalcula todo de cero contra el servicio de geometría
        // a partir de segmentos_base_json, que es la única fuente que sigue viva en el código.
        // Idempotente por diseño (solo corre para una Pieza sin fila esBase=true) y no debe
        // tumbar el arranque si el servicio de geometría está caído: se loguea y se sigue, la
        // Pieza queda pendiente de re-intentar en el próximo arranque.
        for (Pieza pieza : piezaRepository.findAll()) {
            if (piezaTalleRepository.findByPieza_IdAndEsBaseTrue(pieza.getId()).isPresent()) {
                continue;
            }
            try {
                List<SegmentoDto> segmentos = objectMapper.readValue(
                    pieza.getSegmentosBaseJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, SegmentoDto.class));
                PiezaGeometriaCalculoResponse calculo = piezaGeometriaClient.calcularBase(segmentos, pieza.isSimetrica());

                piezaTalleRepository.save(PiezaTalle.builder()
                    .pieza(pieza)
                    .talle(pieza.getTalleBase())
                    .coordenadasJson(objectMapper.writeValueAsString(calculo.coordenadas()))
                    .areaCm2(calculo.areaCm2())
                    .anchoCm(calculo.anchoCm())
                    .largoCm(calculo.largoCm())
                    .perimetroCm(calculo.perimetroCm())
                    .esBase(true)
                    .editadoManualmente(false)
                    .fechaGeneracion(LocalDateTime.now())
                    .build());
                System.out.println("--- MIGRACIÓN PiezaTalle: base generada para Pieza id=" + pieza.getId()
                    + " (" + pieza.getNombre() + ") ---");
            } catch (Exception error) {
                System.out.println("--- MIGRACIÓN PiezaTalle: NO se pudo generar la base de Pieza id=" + pieza.getId()
                    + " (" + pieza.getNombre() + "): " + error.getMessage() + " — se reintenta en el próximo arranque ---");
            }
        }

        System.out.println("--- DATOS SEMILLA CARGADOS CORRECTAMENTE ---");
    }
}
