package com.weclover.backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.config.CorteProperties;
import com.weclover.backend.dto.corte.CantidadPorColorResponse;
import com.weclover.backend.dto.corte.FaltanteCargaCorteResponse;
import com.weclover.backend.dto.corte.GeometriaTalleResponse;
import com.weclover.backend.dto.corte.GrupoTelaCorteResponse;
import com.weclover.backend.dto.corte.ItemCorteResponse;
import com.weclover.backend.dto.corte.MotivoPendienteCorte;
import com.weclover.backend.dto.corte.OrigenItemCorteResponse;
import com.weclover.backend.dto.corte.ParametrosCorteResponse;
import com.weclover.backend.dto.corte.PendienteCorteResponse;
import com.weclover.backend.dto.corte.PendientesPorMotivoResponse;
import com.weclover.backend.dto.corte.PiezaCorteResponse;
import com.weclover.backend.dto.corte.PinPendienteResponse;
import com.weclover.backend.dto.corte.PreparacionCorteResponse;
import com.weclover.backend.dto.corte.ResumenCorteResponse;
import com.weclover.backend.dto.corte.TipoFaltanteCorte;
import com.weclover.backend.entity.AlumnoProductoTalle;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.PatronCorte;
import com.weclover.backend.entity.PatronCorteColor;
import com.weclover.backend.entity.PatronCortePosicionPieza;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Pieza;
import com.weclover.backend.entity.PiezaTalle;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.TablaTalle;
import com.weclover.backend.entity.Tanda;
import com.weclover.backend.entity.TipoTela;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.repository.AlumnoProductoTalleRepository;
import com.weclover.backend.repository.PatronCorteRepository;
import com.weclover.backend.repository.PiezaTalleRepository;
import com.weclover.backend.repository.ProductoInsumoSecundarioRepository;
import com.weclover.backend.repository.ProductoRepository;
import com.weclover.backend.repository.TandaRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Preparación del corte de una tanda (Módulo 4, paso 2): la demanda de piezas que recibe el
 * algoritmo de corte. Solo lectura: no invoca el algoritmo ni guarda nada.
 *
 * - Entran los productos habilitados y no-Bandera de los pedidos de la tanda que no están
 *   CANCELADO ni ENTREGADO (no hace falta que estén LISTO_PARA_PRODUCCION).
 * - Clave de agrupación: tela + pieza + talle. El color no agrupa: piezas iguales de colores
 *   distintos se dibujan una vez y se cortan en capas de cada color.
 * - Cantidad = unidades del producto con ese talle × pines de esa pieza (cada pin es una
 *   instancia). La tela y el color de cada pin los decide ResolucionTelaPin; acá no se asumen.
 * - Lo que no entra va a `pendientes` con su motivo, sin frenar al resto.
 *
 * Todo lo que el cálculo necesita se trae en lote al principio (ver cargarEnLote), con un
 * número fijo de consultas que no depende de cuántos productos o pines haya.
 */
@Service
@RequiredArgsConstructor
public class PreparacionCorteService {

    private static final Set<String> ROLES_LECTURA = Set.of("ROLE_ADMINISTRATIVO", "ROLE_PLANTA");
    private static final Set<EstadoPedido> ESTADOS_EXCLUIDOS = Set.of(EstadoPedido.CANCELADO, EstadoPedido.ENTREGADO);

    private final TandaRepository tandaRepository;
    private final ProductoRepository productoRepository;
    private final ProductoInsumoSecundarioRepository productoInsumoSecundarioRepository;
    private final PatronCorteRepository patronCorteRepository;
    private final PiezaTalleRepository piezaTalleRepository;
    private final AlumnoProductoTalleRepository alumnoProductoTalleRepository;
    private final AutorizacionService autorizacionService;
    private final CorteProperties corteProperties;
    private final ObjectMapper objectMapper;

    /** Todo lo que se trajo en lote, indexado para el cálculo. */
    record DatosCorte(
        Map<Long, List<AlumnoProductoTalle>> unidadesPorProducto,
        Map<String, PiezaTalle> geometriaPorPiezaYTalle
    ) {
        PiezaTalle geometria(Pieza pieza, TablaTalle talle) {
            return geometriaPorPiezaYTalle.get(clavePiezaTalle(pieza.getId(), talle.getId()));
        }
    }

    @Transactional(readOnly = true)
    public PreparacionCorteResponse preparar(Long idTanda, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_LECTURA);

        Tanda tanda = tandaRepository.findById(idTanda)
            .orElseThrow(() -> new ResourceNotFoundException("No existe la tanda con id " + idTanda));

        List<Producto> productos = productoRepository.buscarParaCorte(idTanda).stream()
            .filter(producto -> !EtapaProduccionAplicabilidad.esBandera(producto))
            .filter(producto -> !ESTADOS_EXCLUIDOS.contains(producto.getPedido().getEstadoActual()))
            .sorted(Comparator.comparing((Producto p) -> p.getPedido().getCodigoInterno(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Producto::getId))
            .toList();

        DatosCorte datos = cargarEnLote(productos);

        Demanda demanda = new Demanda();
        List<PendienteCorteResponse> pendientes = new ArrayList<>();
        Totales totales = new Totales();
        Faltantes faltantes = new Faltantes();
        for (Producto producto : productos) {
            evaluarProducto(producto, datos, demanda, pendientes, totales, faltantes);
        }

        return new PreparacionCorteResponse(
            tanda.getId(),
            tanda.getNombre(),
            parametros(),
            construirPiezas(demanda, datos),
            demanda.construirGrupos(),
            pendientes,
            construirResumen(demanda, pendientes, totales, faltantes));
    }

    // ---------- Carga en lote ----------

    /**
     * Un número fijo de consultas, sin importar cuántos productos, posiciones o pines haya:
     * colores, insumos y sus asignaciones, posiciones y pines de las molderías (con pieza y tipo
     * de insumo), unidades con su talle y geometría por talle. Las que inicializan colecciones
     * vuelven a consultar las mismas entidades dentro de esta transacción, así que después
     * ResolucionTelaPin y AsignacionInsumoPieza navegan el grafo sin disparar más consultas.
     */
    DatosCorte cargarEnLote(List<Producto> productos) {
        if (productos.isEmpty()) {
            return new DatosCorte(Map.of(), Map.of());
        }
        productoRepository.cargarColores(productos);
        productoRepository.cargarInsumosSecundarios(productos);
        productoInsumoSecundarioRepository.cargarAsignaciones(productos);

        Set<PatronCorte> patrones = productos.stream()
            .map(Producto::getPatronCorte)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> idsPieza = new LinkedHashSet<>();
        if (!patrones.isEmpty()) {
            patronCorteRepository.cargarPosiciones(patrones);
            for (PatronCorteColor posicion : patronCorteRepository.cargarPines(patrones)) {
                posicion.getPosicionesPieza().forEach(pin -> idsPieza.add(pin.getPieza().getId()));
            }
        }

        Map<String, PiezaTalle> geometria = new LinkedHashMap<>();
        if (!idsPieza.isEmpty()) {
            for (PiezaTalle piezaTalle : piezaTalleRepository.buscarConTallePorPiezas(idsPieza)) {
                geometria.put(clavePiezaTalle(piezaTalle.getPieza().getId(), piezaTalle.getTalle().getId()), piezaTalle);
            }
        }

        Map<Long, List<AlumnoProductoTalle>> unidades = alumnoProductoTalleRepository
            .buscarConTallePorProductos(productos.stream().map(Producto::getId).toList()).stream()
            .collect(Collectors.groupingBy(unidad -> unidad.getProducto().getId()));

        return new DatosCorte(unidades, geometria);
    }

    private static String clavePiezaTalle(Long idPieza, Long idTalle) {
        return idPieza + "|" + idTalle;
    }

    // ---------- Evaluación de un producto ----------

    /**
     * Primero lo que deja pendiente a todo el producto (sin tela, sin moldería, colores o
     * talles incompletos, posiciones sin pines, pines sin resolver): se informan todos los
     * motivos que apliquen, uno por entrada. Si no hay ninguno, cada unidad entra salvo las de
     * talle personalizado y las de un talle donde a alguna pieza le falta geometría.
     *
     * De paso cuenta las unidades incluidas y pendientes del producto (cada unidad una sola vez,
     * tenga los motivos que tenga) y anota qué falta cargar para destrabar lo pendiente.
     */
    private void evaluarProducto(Producto producto, DatosCorte datos, Demanda demanda, List<PendienteCorteResponse> pendientes,
            Totales totales, Faltantes faltantes) {
        List<PendienteCorteResponse> delProducto = new ArrayList<>();
        int total = producto.getCantidadTotal();
        PatronCorte patron = producto.getPatronCorte();
        List<AlumnoProductoTalle> unidades = datos.unidadesPorProducto().getOrDefault(producto.getId(), List.of());

        if (producto.getTipoTela() == null) {
            delProducto.add(pendiente(producto, MotivoPendienteCorte.SIN_TELA, "La prenda no tiene tela asignada", total));
        }
        if (patron == null) {
            delProducto.add(pendiente(producto, MotivoPendienteCorte.SIN_MOLDERIA, "La prenda no tiene moldería asignada", total));
        } else {
            int posiciones = patron.getColores().size();
            if (producto.getColores().size() != posiciones) {
                delProducto.add(pendiente(producto, MotivoPendienteCorte.COLORES_INCOMPLETOS,
                    "Faltan colores por marcar (" + producto.getColores().size() + " de " + posiciones + " posiciones)", total));
                faltantes.agregar(TipoFaltanteCorte.PRODUCTO_SIN_COLORES, producto.getId(),
                    "Ficha Técnica: marcar los colores de " + describir(producto), null, total);
            }
            List<Integer> sinPines = patron.getColores().stream()
                .filter(posicion -> posicion.getPosicionesPieza().isEmpty())
                .map(PatronCorteColor::getOrden)
                .sorted()
                .toList();
            if (!sinPines.isEmpty()) {
                delProducto.add(pendiente(producto, MotivoPendienteCorte.POSICION_SIN_PINES,
                    "La moldería \"" + patron.getNombre() + "\" no tiene piezas en "
                        + (sinPines.size() == 1 ? "la posición " : "las posiciones ") + unir(sinPines), total));
                faltantes.agregar(TipoFaltanteCorte.MOLDERIA_SIN_PINES, patron.getId(),
                    "Molderías: cargar las piezas de #" + patron.getNumeroInterno() + " " + patron.getNombre(),
                    (sinPines.size() == 1 ? "posición " : "posiciones ") + unir(sinPines), total);
            }
        }
        if (!CargaTallesService.tallesCompletosDelProducto(producto, unidades)) {
            delProducto.add(pendiente(producto, MotivoPendienteCorte.TALLES_INCOMPLETOS,
                "Faltan medidas: " + contarMedidas(unidades) + " de " + total + " unidades con talle cargado", total));
        } else if (producto.getTipoPrenda() == null || producto.getTipoPrenda().getGrupoTalle() == null) {
            delProducto.add(pendiente(producto, MotivoPendienteCorte.TALLES_INCOMPLETOS,
                "La prenda no usa talles: no se puede elegir la geometría de sus piezas", total));
        }

        // Los pines solo se resuelven si el cuerpo está completo: sin tela, sin moldería o con
        // colores sin marcar, todas las piezas del cuerpo repetirían CUERPO_INCOMPLETO.
        Map<PatronCortePosicionPieza, ResolucionTelaPin.Resultado> resueltos = new LinkedHashMap<>();
        boolean cuerpoCompleto = delProducto.stream().noneMatch(p -> p.motivo() == MotivoPendienteCorte.SIN_TELA
            || p.motivo() == MotivoPendienteCorte.SIN_MOLDERIA || p.motivo() == MotivoPendienteCorte.COLORES_INCOMPLETOS);
        if (cuerpoCompleto && patron != null) {
            Map<MotivoPendienteCorte, List<PatronCortePosicionPieza>> sinResolver = new EnumMap<>(MotivoPendienteCorte.class);
            for (PatronCortePosicionPieza pin : AsignacionInsumoPieza.pines(producto)) {
                ResolucionTelaPin.Resultado resultado = ResolucionTelaPin.resolver(producto, pin);
                if (resultado.resuelto()) {
                    resueltos.put(pin, resultado);
                } else {
                    sinResolver.computeIfAbsent(MotivoPendienteCorte.valueOf(resultado.motivo().name()), m -> new ArrayList<>()).add(pin);
                }
            }
            sinResolver.forEach((motivo, pines) -> delProducto.add(new PendienteCorteResponse(
                motivo, textoPinesSinResolver(motivo, pines.size()),
                producto.getPedido().getId(), producto.getPedido().getCodigoInterno(), producto.getId(), nombrePrenda(producto),
                total, null, null,
                pines.stream().map(pin -> new PinPendienteResponse(pin.getId(), AsignacionInsumoPieza.etiqueta(producto, pin),
                    pin.getPieza().getId(), pin.getPieza().getNombre())).toList())));
            List<PatronCortePosicionPieza> sinAsignar = sinResolver.getOrDefault(MotivoPendienteCorte.SIN_ASIGNAR, List.of());
            if (!sinAsignar.isEmpty()) {
                faltantes.agregar(TipoFaltanteCorte.PIN_SIN_ASIGNAR, producto.getId(),
                    "Ficha Técnica: asignar los pines de " + describir(producto),
                    sinAsignar.stream().map(pin -> AsignacionInsumoPieza.etiqueta(producto, pin)).collect(Collectors.joining(", ")),
                    total);
            }
        }

        if (!delProducto.isEmpty()) {
            pendientes.addAll(delProducto);
            totales.pendientes += total;
            return;
        }

        // Unidades por talle; las de talle personalizado (o sin talle) quedan afuera.
        Map<TablaTalle, Integer> unidadesPorTalle = new TreeMap<>(Comparator.comparingInt(TablaTalle::getOrden));
        int personalizadas = 0;
        for (AlumnoProductoTalle unidad : unidades) {
            if (unidad.isPersonalizado() || unidad.getTalleAsignado() == null) {
                personalizadas++;
            } else {
                unidadesPorTalle.merge(unidad.getTalleAsignado(), 1, Integer::sum);
            }
        }
        if (personalizadas > 0) {
            pendientes.add(pendiente(producto, MotivoPendienteCorte.TALLE_PERSONALIZADO,
                personalizadas + (personalizadas == 1 ? " unidad con talle personalizado" : " unidades con talle personalizado")
                    + ": no se escala la moldería", personalizadas));
            totales.pendientes += personalizadas;
        }

        for (Map.Entry<TablaTalle, Integer> entrada : unidadesPorTalle.entrySet()) {
            TablaTalle talle = entrada.getKey();
            int cantidad = entrada.getValue();

            List<Pieza> sinGeometria = resueltos.keySet().stream()
                .map(PatronCortePosicionPieza::getPieza)
                .filter(pieza -> datos.geometria(pieza, talle) == null)
                .distinct()
                .toList();
            if (!sinGeometria.isEmpty()) {
                pendientes.add(new PendienteCorteResponse(
                    MotivoPendienteCorte.PIEZA_SIN_TALLE,
                    (sinGeometria.size() == 1 ? "Una pieza no tiene" : sinGeometria.size() + " piezas no tienen")
                        + " geometría para el talle " + talle.getTalle(),
                    producto.getPedido().getId(), producto.getPedido().getCodigoInterno(), producto.getId(), nombrePrenda(producto),
                    cantidad, talle.getId(), talle.getTalle(),
                    sinGeometria.stream().map(pieza -> new PinPendienteResponse(null, null, pieza.getId(), pieza.getNombre())).toList()));
                totales.pendientes += cantidad;
                sinGeometria.forEach(pieza -> faltantes.agregar(TipoFaltanteCorte.PIEZA_SIN_TALLE_GRADUADO, pieza.getId(),
                    "Piezas: graduar " + pieza.getNombre(), "talle " + talle.getTalle(), cantidad));
                continue;
            }

            resueltos.forEach((pin, resultado) -> demanda.sumar(producto, pin.getPieza(), talle, resultado, cantidad));
            totales.incluidas += cantidad;
        }
    }

    private static String describir(Producto producto) {
        return nombrePrenda(producto) + " #" + producto.getId() + " (pedido " + producto.getPedido().getCodigoInterno() + ")";
    }

    // ---------- Resumen ----------

    static final String NOTA_PENDIENTES_POR_MOTIVO =
        "Una unidad puede aparecer en más de un motivo; los totales por motivo no suman el total de pendientes.";

    /** Unidades incluidas y pendientes, contadas una sola vez por unidad. */
    private static final class Totales {
        private int incluidas;
        private int pendientes;
    }

    /** Lo que falta cargar, agrupado por (tipo, referencia), con las unidades que destrabaría. */
    private static final class Faltantes {
        private static final class Faltante {
            private final TipoFaltanteCorte tipo;
            private final Long idReferencia;
            private final String titulo;
            private final Set<String> detalles = new LinkedHashSet<>();
            private int unidades;

            private Faltante(TipoFaltanteCorte tipo, Long idReferencia, String titulo) {
                this.tipo = tipo;
                this.idReferencia = idReferencia;
                this.titulo = titulo;
            }
        }

        private final Map<String, Faltante> porClave = new LinkedHashMap<>();

        void agregar(TipoFaltanteCorte tipo, Long idReferencia, String titulo, String detalle, int unidades) {
            Faltante faltante = porClave.computeIfAbsent(tipo + "|" + idReferencia, clave -> new Faltante(tipo, idReferencia, titulo));
            if (detalle != null) {
                faltante.detalles.add(detalle);
            }
            faltante.unidades += unidades;
        }

        /** Primero lo que más unidades destraba. */
        List<FaltanteCargaCorteResponse> construir() {
            return porClave.values().stream()
                .sorted(Comparator.comparingInt((Faltante f) -> -f.unidades).thenComparing(f -> f.tipo).thenComparing(f -> f.titulo))
                .map(f -> new FaltanteCargaCorteResponse(f.tipo, f.idReferencia,
                    f.detalles.isEmpty() ? f.titulo : f.titulo + " (" + String.join(", ", f.detalles) + ")", f.unidades))
                .toList();
        }
    }

    private static ResumenCorteResponse construirResumen(Demanda demanda, List<PendienteCorteResponse> pendientes,
            Totales totales, Faltantes faltantes) {
        Map<MotivoPendienteCorte, int[]> porMotivo = new EnumMap<>(MotivoPendienteCorte.class);
        for (PendienteCorteResponse pendiente : pendientes) {
            int[] acumulado = porMotivo.computeIfAbsent(pendiente.motivo(), m -> new int[2]);
            acumulado[0]++;
            acumulado[1] += pendiente.unidadesAfectadas();
        }
        List<PendientesPorMotivoResponse> pendientesPorMotivo = porMotivo.entrySet().stream()
            .map(entrada -> new PendientesPorMotivoResponse(entrada.getKey(), entrada.getValue()[0], entrada.getValue()[1]))
            .toList();
        int piezasDistintas = (int) demanda.items().stream().map(item -> item.pieza.getId()).distinct().count();

        return new ResumenCorteResponse(
            totales.incluidas,
            totales.pendientes,
            piezasDistintas,
            pendientesPorMotivo,
            NOTA_PENDIENTES_POR_MOTIVO,
            faltantes.construir());
    }

    private static PendienteCorteResponse pendiente(Producto producto, MotivoPendienteCorte motivo, String detalle, int unidades) {
        return new PendienteCorteResponse(motivo, detalle, producto.getPedido().getId(), producto.getPedido().getCodigoInterno(),
            producto.getId(), nombrePrenda(producto), unidades, null, null, List.of());
    }

    private static String textoPinesSinResolver(MotivoPendienteCorte motivo, int pines) {
        String cuantos = pines == 1 ? "Una pieza" : pines + " piezas";
        return switch (motivo) {
            case FALTA_INSUMO -> cuantos + " se corta(n) en un insumo que la ficha no tiene cargado";
            case SIN_ASIGNAR -> cuantos + " sin fila de insumo asignada (la ficha tiene varias de ese tipo)";
            case INSUMO_INCOMPLETO -> cuantos + " con un insumo sin tela o sin color";
            case TELA_NO_CORTABLE -> cuantos + " en una tela que no se compra por peso";
            case CUERPO_INCOMPLETO -> cuantos + " del cuerpo sin tela o sin color";
            default -> cuantos + " sin resolver";
        };
    }

    private static int contarMedidas(List<AlumnoProductoTalle> unidades) {
        return (int) unidades.stream().filter(u -> u.getAnchoCm() != null && u.getLargoCm() != null).count();
    }

    private static String nombrePrenda(Producto producto) {
        return producto.getTipoPrenda() != null ? producto.getTipoPrenda().getNombre() : null;
    }

    private static String unir(List<Integer> numeros) {
        List<String> textos = numeros.stream().map(String::valueOf).toList();
        if (textos.size() == 1) {
            return textos.get(0);
        }
        return String.join(", ", textos.subList(0, textos.size() - 1)) + " y " + textos.get(textos.size() - 1);
    }

    // ---------- Armado de la respuesta ----------

    private ParametrosCorteResponse parametros() {
        return new ParametrosCorteResponse(
            corteProperties.anchoUtilCm(),
            corteProperties.largoMaximoCm(),
            corteProperties.margenCorteCm(),
            corteProperties.capasMaximas(),
            corteProperties.telasPorCapa());
    }

    /** Solo las piezas y talles que aparecen en la demanda, con su geometría. */
    private List<PiezaCorteResponse> construirPiezas(Demanda demanda, DatosCorte datos) {
        Map<Pieza, Set<TablaTalle>> tallesPorPieza = new TreeMap<>(Comparator.comparing(Pieza::getNombre).thenComparing(Pieza::getId));
        demanda.items().forEach(item -> tallesPorPieza
            .computeIfAbsent(item.pieza, p -> new TreeSet<>(Comparator.comparingInt(TablaTalle::getOrden)))
            .add(item.talle));

        return tallesPorPieza.entrySet().stream()
            .map(entrada -> new PiezaCorteResponse(
                entrada.getKey().getId(),
                entrada.getKey().getNombre(),
                entrada.getKey().isSimetrica(),
                entrada.getValue().stream().map(talle -> {
                    PiezaTalle geometria = datos.geometria(entrada.getKey(), talle);
                    return new GeometriaTalleResponse(talle.getId(), talle.getTalle(), coordenadas(geometria),
                        geometria.getAnchoCm(), geometria.getLargoCm(), geometria.getAreaCm2());
                }).toList()))
            .toList();
    }

    private List<double[]> coordenadas(PiezaTalle geometria) {
        return objectMapper.readValue(geometria.getCoordenadasJson(),
            objectMapper.getTypeFactory().constructCollectionType(List.class, double[].class));
    }

    // ---------- Acumulación de la demanda ----------

    /** Ítem en construcción: una pieza en un talle dentro de una tela. */
    private static final class ItemAcumulado {
        private final Pieza pieza;
        private final TablaTalle talle;
        private final Map<Long, PaletaColores> colores = new LinkedHashMap<>();
        private final Map<Long, Integer> cantidadPorColor = new LinkedHashMap<>();
        private final Map<String, OrigenItemCorteResponse> origen = new LinkedHashMap<>();

        private ItemAcumulado(Pieza pieza, TablaTalle talle) {
            this.pieza = pieza;
            this.talle = talle;
        }
    }

    /** Toda la demanda, agrupada por tela y, dentro de cada tela, por pieza + talle. */
    private static final class Demanda {
        private final Map<Long, TipoTela> telas = new LinkedHashMap<>();
        private final Map<Long, Map<String, ItemAcumulado>> itemsPorTela = new LinkedHashMap<>();

        /** Suma `cantidad` piezas de ese pin (una por unidad del talle) en su tela y su color. */
        void sumar(Producto producto, Pieza pieza, TablaTalle talle, ResolucionTelaPin.Resultado resultado, int cantidad) {
            TipoTela tela = resultado.tela();
            PaletaColores color = resultado.color();
            telas.putIfAbsent(tela.getId(), tela);
            ItemAcumulado item = itemsPorTela.computeIfAbsent(tela.getId(), id -> new LinkedHashMap<>())
                .computeIfAbsent(clavePiezaTalle(pieza.getId(), talle.getId()), clave -> new ItemAcumulado(pieza, talle));

            item.colores.putIfAbsent(color.getId(), color);
            item.cantidadPorColor.merge(color.getId(), cantidad, Integer::sum);

            Pedido pedido = producto.getPedido();
            Long idInsumo = resultado.insumo() != null ? resultado.insumo().getId() : null;
            String claveOrigen = producto.getId() + "|" + color.getId() + "|" + idInsumo;
            item.origen.merge(claveOrigen,
                new OrigenItemCorteResponse(pedido.getId(), pedido.getCodigoInterno(), producto.getId(), color.getId(), cantidad, idInsumo),
                (previo, nuevo) -> new OrigenItemCorteResponse(previo.idPedido(), previo.codigoInternoPedido(), previo.idProducto(),
                    previo.idPaletaColor(), previo.cantidad() + nuevo.cantidad(), previo.idProductoInsumo()));
        }

        List<ItemAcumulado> items() {
            return itemsPorTela.values().stream().flatMap(items -> items.values().stream()).toList();
        }

        List<GrupoTelaCorteResponse> construirGrupos() {
            return telas.values().stream()
                .sorted(Comparator.comparing(TipoTela::getNombre))
                .map(tela -> new GrupoTelaCorteResponse(
                    tela.getId(), tela.getCodigo(), tela.getNombre(),
                    itemsPorTela.get(tela.getId()).values().stream()
                        .sorted(Comparator.comparing((ItemAcumulado i) -> i.pieza.getNombre())
                            .thenComparing(i -> i.pieza.getId())
                            .thenComparingInt(i -> i.talle.getOrden()))
                        .map(Demanda::aItemResponse)
                        .toList()))
                .toList();
        }

        private static ItemCorteResponse aItemResponse(ItemAcumulado item) {
            List<CantidadPorColorResponse> porColor = item.cantidadPorColor.entrySet().stream()
                .map(entrada -> {
                    PaletaColores color = item.colores.get(entrada.getKey());
                    return new CantidadPorColorResponse(color.getId(), color.getNombre(), color.getHex(), entrada.getValue());
                })
                .sorted(Comparator.comparing(CantidadPorColorResponse::nombreColor))
                .toList();
            int total = porColor.stream().mapToInt(CantidadPorColorResponse::cantidad).sum();
            return new ItemCorteResponse(item.pieza.getId(), item.talle.getId(), item.talle.getTalle(), porColor, total,
                List.copyOf(item.origen.values()));
        }
    }
}
