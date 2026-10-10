package com.weclover.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.planificacioncompra.ArticuloResumenResponse;
import com.weclover.backend.dto.planificacioncompra.PlanificacionCompraBorradorRequest;
import com.weclover.backend.dto.planificacioncompra.PlanificacionCompraBorradorResponse;
import com.weclover.backend.dto.planificacioncompra.PlanificacionCompraDetalleResponse;
import com.weclover.backend.dto.planificacioncompra.PlanificacionCompraResponse;
import com.weclover.backend.dto.planificacioncompra.PlanificacionResumenResponse;
import com.weclover.backend.dto.planificacioncompra.ProductoElegibleResponse;
import com.weclover.backend.dto.produccion.ProduccionTandaResponse;
import com.weclover.backend.entity.ArticuloProveedor;
import com.weclover.backend.entity.ArticuloStock;
import com.weclover.backend.entity.EstadoPlanificacionCompra;
import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.PlanificacionCompra;
import com.weclover.backend.entity.PlanificacionCompraDetalle;
import com.weclover.backend.entity.PlanificacionCompraProductoBorrador;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoColor;
import com.weclover.backend.entity.ProductoInsumoSecundario;
import com.weclover.backend.entity.Stock;
import com.weclover.backend.entity.TipoTela;
import com.weclover.backend.entity.UnidadMedida;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.repository.ArticuloProveedorRepository;
import com.weclover.backend.repository.ArticuloStockRepository;
import com.weclover.backend.repository.PlanificacionCompraDetalleRepository;
import com.weclover.backend.repository.PlanificacionCompraRepository;
import com.weclover.backend.repository.ProductoRepository;
import com.weclover.backend.repository.StockRepository;
import com.weclover.backend.repository.UsuarioRepository;
import com.weclover.backend.service.TandaService.TandaCalculada;

import lombok.RequiredArgsConstructor;

/**
 * Planificador de Compras (Fase 3): a partir de un conjunto de productos con "diseño completo"
 * (ver ProductoService.motivoDisenoIncompleto), genera una foto de cuánto comprar de cada
 * (TipoTela, PaletaColores), agrupable por producto/pedido o unificado con estimado en pesos.
 * Mismo criterio de roles que Proveedor/ArticuloProveedor (Fase 2): solo ROLE_ADMINISTRATIVO,
 * lectura y escritura, por tratarse de datos de compras/costos.
 */
@Service
@RequiredArgsConstructor
public class PlanificacionCompraService {

    private static final Set<String> ROLES_GESTION_COMPRAS = Set.of("ROLE_ADMINISTRATIVO");

    private final PlanificacionCompraRepository planificacionCompraRepository;
    private final PlanificacionCompraDetalleRepository planificacionCompraDetalleRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ArticuloProveedorRepository articuloProveedorRepository;
    private final ArticuloStockRepository articuloStockRepository;
    private final StockRepository stockRepository;
    private final ProductoService productoService;
    private final TandaService tandaService;
    private final AutorizacionService autorizacionService;

    /** Clave de agrupación de un detalle: qué tela+color, más allá de en qué producto/insumo se originó. */
    private record ClaveArticulo(TipoTela tipoTela, PaletaColores paletaColor) {
    }

    @Transactional(readOnly = true)
    public List<ProductoElegibleResponse> listarProductosElegibles(Long idColegio, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_COMPRAS);

        List<Producto> productos = productoRepository.buscarElegiblesPlanificacion(idColegio);

        // Tanda de cada pedido con su estado y posición derivados (ver TandaService), para el
        // filtro por tanda de la pantalla.
        Map<Long, ProduccionTandaResponse> tandaPorIdPedido = new HashMap<>();
        for (TandaCalculada calculada : tandaService.calcularTandas()) {
            ProduccionTandaResponse tanda = new ProduccionTandaResponse(
                calculada.tanda().getId(), calculada.tanda().getNombre(), calculada.posicion(), calculada.estado());
            calculada.pedidos().forEach(pedido -> tandaPorIdPedido.put(pedido.getId(), tanda));
        }

        // El % pagado es sobre el pedido completo, no sobre el subconjunto de productos que
        // cayó en el rango filtrado — por eso se recorren todos los productos de cada pedido
        // involucrado (no solo los "elegibles"), igual que PedidoService.construirRespuesta.
        Map<Long, Pedido> pedidosPorId = new LinkedHashMap<>();
        for (Producto producto : productos) {
            pedidosPorId.putIfAbsent(producto.getPedido().getId(), producto.getPedido());
        }
        Map<Long, Float> precioTotalPorPedido = new HashMap<>();
        for (Pedido pedido : pedidosPorId.values()) {
            float total = 0f;
            for (Producto p : pedido.getProductos()) {
                total += p.getCantidadTotal() * p.getCosto();
            }
            precioTotalPorPedido.put(pedido.getId(), total);
        }

        List<Long> idsProductos = productos.stream().map(Producto::getId).toList();
        Map<Long, List<PlanificacionResumenResponse>> planificacionesPorProducto = new HashMap<>();
        for (PlanificacionCompraDetalle detalle : planificacionCompraDetalleRepository.findByProducto_IdIn(idsProductos)) {
            Long idProducto = detalle.getProducto().getId();
            PlanificacionResumenResponse resumen = new PlanificacionResumenResponse(
                detalle.getPlanificacionCompra().getId(), detalle.getPlanificacionCompra().getNombre());
            List<PlanificacionResumenResponse> lista = planificacionesPorProducto.computeIfAbsent(idProducto, k -> new ArrayList<>());
            if (!lista.contains(resumen)) {
                lista.add(resumen);
            }
        }

        return productos.stream()
            .map(producto -> {
                Pedido pedido = producto.getPedido();
                float precioTotalPedido = precioTotalPorPedido.getOrDefault(pedido.getId(), 0f);
                float porcentajePagado = precioTotalPedido > 0 ? (pedido.getPagoInicial() / precioTotalPedido) * 100 : 0f;
                Optional<String> motivo = productoService.motivoDisenoIncompleto(producto);

                return new ProductoElegibleResponse(
                    productoService.construirRespuesta(producto),
                    pedido.getId(),
                    pedido.getCodigoInterno(),
                    pedido.getColegio().getNombre(),
                    pedido.getEstadoActual(),
                    tandaPorIdPedido.get(pedido.getId()),
                    pedido.getFechaVenta(),
                    pedido.getFechaEstimadaEntrega(),
                    porcentajePagado,
                    motivo.isEmpty(),
                    motivo.orElse(null),
                    producto.getPatronCorte() != null ? producto.getPatronCorte().getNombre() : null,
                    producto.getPatronCorte() != null ? producto.getPatronCorte().getNumeroInterno() : null,
                    planificacionesPorProducto.getOrDefault(producto.getId(), List.of())
                );
            })
            .toList();
    }

    /**
     * Crea (idExistente=null) o actualiza (idExistente presente) una planificación en estado
     * BORRADOR. Sin validaciones de completitud a propósito — un borrador puede guardarse a
     * mitad de completar (nombre vacío, fechas sin elegir, productos con diseño incompleto
     * tildados junto a otros que sí lo tienen). Pensado para llamarse seguido (autoguardado del
     * front mientras el usuario edita), no solo al confirmar.
     */
    @Transactional
    public PlanificacionCompraResponse guardarBorrador(Long idExistente, PlanificacionCompraBorradorRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_COMPRAS);

        PlanificacionCompra planificacion;
        if (idExistente != null) {
            planificacion = obtenerPlanificacion(idExistente);
            if (planificacion.getEstado() != EstadoPlanificacionCompra.BORRADOR) {
                throw new BusinessRuleException("Esta planificación ya está confirmada, no se puede editar como borrador");
            }
        } else {
            Usuario actor = usuarioRepository.findById(idUsuarioActor)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el usuario que crea la planificación"));
            planificacion = PlanificacionCompra.builder()
                .estado(EstadoPlanificacionCompra.BORRADOR)
                .fechaCreacion(LocalDateTime.now())
                .creadoPor(actor)
                .build();
        }

        planificacion.setNombre(request.nombre() != null ? request.nombre().trim() : "");

        List<Long> idsUnicos = request.idsProductos() != null
            ? request.idsProductos().stream().distinct().toList()
            : List.of();
        List<Producto> productos = productoRepository.findAllById(idsUnicos);
        if (productos.size() != idsUnicos.size()) {
            throw new ResourceNotFoundException("Alguno de los productos seleccionados no existe");
        }

        /*
         * Reconcilia la selección en vez de `clear()` + volver a agregar todo: sobre una
         * colección `orphanRemoval = true`, un `clear()` seguido de altas que repiten la misma
         * clave (id_planificacion_compra, id_producto) tira `DataIntegrityViolationException`
         * — Hibernate ordena los INSERT antes que los DELETE dentro del mismo flush, así que el
         * alta "nueva" de un producto que ya estaba choca contra la baja vieja, todavía no
         * ejecutada, de esa misma fila (se reprodujo con el autoguardado: primer guardado con
         * un producto tildado ok, segundo guardado con el mismo producto tildado —
         * "Duplicate entry ... uk_planificacion_borrador_producto"). Solo se tocan las filas que
         * realmente cambian: se borran las que dejaron de estar tildadas y se agregan las
         * nuevas — las que siguen tildadas ni se rozan.
         */
        Set<Long> idsNuevos = new HashSet<>(idsUnicos);
        planificacion.getProductosBorrador().removeIf(pb -> !idsNuevos.contains(pb.getProducto().getId()));
        Set<Long> idsYaPresentes = planificacion.getProductosBorrador().stream()
            .map(pb -> pb.getProducto().getId())
            .collect(Collectors.toSet());
        for (Producto producto : productos) {
            if (idsYaPresentes.contains(producto.getId())) continue;
            planificacion.getProductosBorrador().add(PlanificacionCompraProductoBorrador.builder()
                .planificacionCompra(planificacion)
                .producto(producto)
                .build());
        }

        actualizarPeriodo(planificacion, productos);

        return construirRespuestaCabecera(planificacionCompraRepository.save(planificacion));
    }

    /**
     * El período de la planificación ("Entregas del X al Y") ya no lo carga nadie: es la menor
     * y la mayor fecha estimada de entrega de los pedidos de los productos elegidos. Sin
     * productos queda vacío.
     */
    private void actualizarPeriodo(PlanificacionCompra planificacion, List<Producto> productos) {
        List<LocalDate> fechas = productos.stream()
            .map(producto -> producto.getPedido().getFechaEstimadaEntrega())
            .sorted()
            .toList();
        planificacion.setFechaDesde(fechas.isEmpty() ? null : fechas.get(0));
        planificacion.setFechaHasta(fechas.isEmpty() ? null : fechas.get(fechas.size() - 1));
    }

    /** Para "continuar editando" un borrador desde el listado: trae nombre/ids tal cual
     *  quedaron guardados (los filtros de la pantalla — tanda, pagos, tipo de prenda, fecha —
     *  son solo de UI y no se persisten). */
    @Transactional(readOnly = true)
    public PlanificacionCompraBorradorResponse obtenerBorrador(Long id, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_COMPRAS);

        PlanificacionCompra planificacion = obtenerPlanificacion(id);
        List<Long> idsProductos = planificacion.getProductosBorrador().stream()
            .map(pb -> pb.getProducto().getId())
            .toList();

        return new PlanificacionCompraBorradorResponse(
            planificacion.getId(),
            planificacion.getNombre(),
            idsProductos
        );
    }

    /**
     * Convierte un BORRADOR en CONFIRMADA: acá sí se exige todo lo que guardarBorrador dejaba
     * pasar (nombre, al menos un producto, todos con diseño completo — revalida server-
     * side por si el front quedó desactualizado entre que se abrió la pantalla y se confirmó).
     * Calcula los PlanificacionCompraDetalle (ver calcularDetalles) y vacía productosBorrador,
     * que ya no hace falta una vez que existe el detalle calculado.
     */
    @Transactional
    public PlanificacionCompraResponse confirmar(Long id, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_COMPRAS);

        PlanificacionCompra planificacion = obtenerPlanificacion(id);
        if (planificacion.getEstado() != EstadoPlanificacionCompra.BORRADOR) {
            throw new BusinessRuleException("Esta planificación ya está confirmada");
        }
        if (planificacion.getNombre() == null || planificacion.getNombre().isBlank()) {
            throw new BusinessRuleException("Ingresá un nombre para la planificación antes de confirmar");
        }
        List<Producto> productos = planificacion.getProductosBorrador().stream()
            .map(PlanificacionCompraProductoBorrador::getProducto)
            .toList();
        if (productos.isEmpty()) {
            throw new BusinessRuleException("Debe seleccionar al menos un producto");
        }

        for (Producto producto : productos) {
            Optional<String> motivo = productoService.motivoDisenoIncompleto(producto);
            if (motivo.isPresent()) {
                throw new BusinessRuleException(
                    "El producto #" + producto.getId() + " no tiene el diseño completo: " + motivo.get());
            }
        }

        List<PlanificacionCompraDetalle> detalles = new ArrayList<>();
        for (Producto producto : productos) {
            detalles.addAll(calcularDetalles(planificacion, producto));
        }
        planificacion.getDetalles().addAll(detalles);
        actualizarPeriodo(planificacion, productos);
        planificacion.getProductosBorrador().clear();
        planificacion.setEstado(EstadoPlanificacionCompra.CONFIRMADA);

        return construirRespuestaCabecera(planificacionCompraRepository.save(planificacion));
    }

    /**
     * "Editar" una planificación CONFIRMADA: vuelve a BORRADOR con los mismos productos
     * tildados y se descarta la foto calculada (PlanificacionCompraDetalle), que se vuelve a
     * calcular al confirmar de nuevo — así la edición reusa todo el flujo de borradores
     * (guardarBorrador/confirmar) en vez de tener un segundo camino para modificar detalles.
     * Mientras está reabierta sus productos dejan de figurar como "ya planificados".
     */
    @Transactional
    public PlanificacionCompraResponse reabrir(Long id, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_COMPRAS);

        PlanificacionCompra planificacion = obtenerPlanificacion(id);
        if (planificacion.getEstado() != EstadoPlanificacionCompra.CONFIRMADA) {
            throw new BusinessRuleException("Esta planificación ya está en borrador");
        }

        // Un detalle por (producto, tela, color): se deduplica por producto. Las prendas dadas
        // de baja después de confirmar no vuelven al borrador.
        Map<Long, Producto> productosPorId = new LinkedHashMap<>();
        for (PlanificacionCompraDetalle detalle : planificacion.getDetalles()) {
            Producto producto = detalle.getProducto();
            if (producto.isHabilitado()) {
                productosPorId.putIfAbsent(producto.getId(), producto);
            }
        }
        for (Producto producto : productosPorId.values()) {
            planificacion.getProductosBorrador().add(PlanificacionCompraProductoBorrador.builder()
                .planificacionCompra(planificacion)
                .producto(producto)
                .build());
        }
        planificacion.getDetalles().clear();
        planificacion.setEstado(EstadoPlanificacionCompra.BORRADOR);

        return construirRespuestaCabecera(planificacionCompraRepository.save(planificacion));
    }

    /** En cualquier estado: una CONFIRMADA también se puede eliminar (se borra con su detalle
     *  calculado). Nada más depende de una planificación — todavía no se conecta con compras
     *  reales ni con stock. */
    @Transactional
    public void eliminar(Long id, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_COMPRAS);

        planificacionCompraRepository.delete(obtenerPlanificacion(id));
    }

    @Transactional(readOnly = true)
    public List<PlanificacionCompraResponse> listar(Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_COMPRAS);

        return planificacionCompraRepository.findAllByOrderByFechaCreacionDesc().stream()
            .map(this::construirRespuestaCabecera)
            .toList();
    }

    @Transactional(readOnly = true)
    public PlanificacionCompraResponse obtenerCabecera(Long id, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_COMPRAS);

        return construirRespuestaCabecera(obtenerPlanificacion(id));
    }

    /** CAMBIO 5: agrupado por (tipoTela, color) a lo largo de toda la planificación, con
     *  estimado en pesos según el proveedor preferido de cada color (si hay uno cargado). */
    @Transactional(readOnly = true)
    public List<ArticuloResumenResponse> obtenerResumen(Long idPlanificacion, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_COMPRAS);

        PlanificacionCompra planificacion = obtenerPlanificacion(idPlanificacion);

        Map<ClaveArticulo, List<PlanificacionCompraDetalle>> agrupado = planificacion.getDetalles().stream()
            .collect(Collectors.groupingBy(d -> new ClaveArticulo(d.getTipoTela(), d.getPaletaColor())));

        List<ArticuloResumenResponse> resumen = new ArrayList<>();
        for (Map.Entry<ClaveArticulo, List<PlanificacionCompraDetalle>> entrada : agrupado.entrySet()) {
            TipoTela tipoTela = entrada.getKey().tipoTela();
            PaletaColores color = entrada.getKey().paletaColor();
            List<PlanificacionCompraDetalle> filas = entrada.getValue();

            float cantidadNecesaria = 0f;
            for (PlanificacionCompraDetalle fila : filas) {
                cantidadNecesaria += fila.getCantidad();
            }
            // La unidad de medida es una "foto" (ver PlanificacionCompraDetalle) — se toma la
            // ya guardada en cualquiera de las filas del grupo, no se recalcula desde el
            // TipoTela actual (podría haber cambiado esPorPeso después de crear la planificación).
            UnidadMedida unidadMedida = filas.get(0).getUnidadMedida();

            // Fase 2: puramente informativo, no descuenta ni modifica Stock (ver clase de
            // ArticuloResumenResponse) — mismo mecanismo para todos los insumos, cierres
            // incluidos, sin caso especial.
            float stockDisponible = articuloStockRepository.findByPaletaColor(color)
                .map(articulo -> stockRepository.findByArticulo(articulo).stream()
                    .map(Stock::getCantidad)
                    .reduce(0f, Float::sum))
                .orElse(0f);
            float cantidadAComprar = Math.max(0f, cantidadNecesaria - stockDisponible);

            Optional<ArticuloProveedor> preferido = articuloProveedorRepository
                .findByPaletaColorAndPreferidoTrue(color)
                .filter(ArticuloProveedor::isActivo)
                .filter(a -> a.getPrecioEstimado() != null);

            Float precioUnitario = preferido.map(ArticuloProveedor::getPrecioEstimado).orElse(null);
            String nombreProveedorPreferido = preferido.map(a -> a.getProveedor().getNombre()).orElse(null);
            // Sobre cantidadAComprar, no cantidadNecesaria — decisión tomada con el usuario: el
            // estimado en pesos debe reflejar lo que realmente hace falta gastar.
            Float estimadoTotal = precioUnitario != null ? precioUnitario * cantidadAComprar : null;

            resumen.add(new ArticuloResumenResponse(
                tipoTela.getId(), tipoTela.getCodigo(), tipoTela.getNombre(),
                color.getId(), color.getNombre(), color.getHex(),
                cantidadNecesaria, stockDisponible, cantidadAComprar, unidadMedida,
                nombreProveedorPreferido, precioUnitario, estimadoTotal
            ));
        }

        return resumen.stream()
            .sorted(Comparator.comparing(ArticuloResumenResponse::nombreTipoTela)
                .thenComparing(ArticuloResumenResponse::nombreColor))
            .toList();
    }

    /** CAMBIO 5: filas sin agrupar, para la vista "por producto/pedido". */
    @Transactional(readOnly = true)
    public List<PlanificacionCompraDetalleResponse> obtenerDetallePorProducto(Long idPlanificacion, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_COMPRAS);

        PlanificacionCompra planificacion = obtenerPlanificacion(idPlanificacion);

        return planificacion.getDetalles().stream()
            .map(detalle -> {
                Producto producto = detalle.getProducto();
                Pedido pedido = producto.getPedido();
                TipoTela tipoTela = detalle.getTipoTela();
                PaletaColores color = detalle.getPaletaColor();

                return new PlanificacionCompraDetalleResponse(
                    detalle.getId(),
                    producto.getId(),
                    producto.getTipoPrenda() != null ? producto.getTipoPrenda().getNombre() : null,
                    pedido.getId(),
                    pedido.getCodigoInterno(),
                    pedido.getColegio().getNombre(),
                    tipoTela.getId(), tipoTela.getCodigo(), tipoTela.getNombre(),
                    color.getId(), color.getNombre(), color.getHex(),
                    detalle.getCantidad(),
                    detalle.getUnidadMedida()
                );
            })
            .sorted(Comparator.comparing(PlanificacionCompraDetalleResponse::codigoInternoPedido)
                .thenComparing(PlanificacionCompraDetalleResponse::idProducto))
            .toList();
    }

    private PlanificacionCompra obtenerPlanificacion(Long id) {
        return planificacionCompraRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("No existe la planificación de compra con id " + id));
    }

    private PlanificacionCompraResponse construirRespuestaCabecera(PlanificacionCompra planificacion) {
        long cantidadProductos = planificacion.getEstado() == EstadoPlanificacionCompra.BORRADOR
            ? planificacion.getProductosBorrador().size()
            : planificacion.getDetalles().stream().map(d -> d.getProducto().getId()).distinct().count();

        return new PlanificacionCompraResponse(
            planificacion.getId(),
            planificacion.getNombre(),
            planificacion.getFechaCreacion(),
            planificacion.getFechaDesde(),
            planificacion.getFechaHasta(),
            planificacion.getCreadoPor().getId(),
            planificacion.getCreadoPor().getNombre(),
            cantidadProductos,
            planificacion.getEstado()
        );
    }

    private static final float GRAMOS_POR_KILOGRAMO = 1000f;

    /**
     * `PatronCorteColor.gramos` y `ProductoInsumoSecundario.cantidad` son gramos por prenda (la
     * receta de la moldería/ficha técnica) — eso no cambia. Pero al agregar el consumo de un
     * lote de prendas para el Planificador de Compras, el resultado tiene que quedar expresado
     * en la unidad en la que efectivamente se compra la tela: kilogramos, no gramos (decisión
     * de negocio confirmada con el usuario — "cuando se muestra la cantidad de material
     * estimado, se muestra en KG"; lo único que queda en gramos es la receta por color dentro
     * de la moldería). Los insumos que se compran por unidad (ej. Cierre) no se convierten.
     */
    private static float convertirAUnidadDeCompra(float cantidadEnGramos, TipoTela tipoTela) {
        return tipoTela.isEsPorPeso() ? cantidadEnGramos / GRAMOS_POR_KILOGRAMO : cantidadEnGramos;
    }

    /**
     * CAMBIO 2: una fila de detalle por cada (tipoTela, color) que este producto consume —
     * tela de cuerpo (gramos de cada posición ya marcada del patrón, según su
     * PatronCorteColor.gramos) más insumos secundarios ya cargados (tal cual están hoy, sin
     * recalcular desde ningún default), todo multiplicado por producto.cantidadTotal, convertido
     * a la unidad de compra (ver convertirAUnidadDeCompra) y fusionado si dos fuentes distintas
     * caen en el mismo (tipoTela, color).
     */
    private List<PlanificacionCompraDetalle> calcularDetalles(PlanificacionCompra planificacion, Producto producto) {
        Map<ClaveArticulo, Float> acumulado = new LinkedHashMap<>();

        TipoTela telaCuerpo = producto.getTipoTela();
        for (ProductoColor color : producto.getColores()) {
            ClaveArticulo clave = new ClaveArticulo(telaCuerpo, color.getPaletaColor());
            float gramos = color.getPatronCorteColor().getGramos() * (float) producto.getCantidadTotal();
            acumulado.merge(clave, convertirAUnidadDeCompra(gramos, telaCuerpo), Float::sum);
        }

        for (ProductoInsumoSecundario insumo : producto.getInsumosSecundarios()) {
            // Filas-flag puras (ej. descripcion="Estampado", ver ProductoInsumoSecundario) no
            // representan consumo de material — tipoTela/color/cantidad quedan en null y no
            // participan del cálculo de compras.
            if (insumo.getTipoTela() == null || insumo.getColor() == null || insumo.getCantidad() == null) {
                continue;
            }
            ClaveArticulo clave = new ClaveArticulo(insumo.getTipoTela(), insumo.getColor());
            float gramos = insumo.getCantidad() * producto.getCantidadTotal();
            acumulado.merge(clave, convertirAUnidadDeCompra(gramos, insumo.getTipoTela()), Float::sum);
        }

        List<PlanificacionCompraDetalle> detalles = new ArrayList<>();
        for (Map.Entry<ClaveArticulo, Float> entrada : acumulado.entrySet()) {
            TipoTela tipoTela = entrada.getKey().tipoTela();
            detalles.add(PlanificacionCompraDetalle.builder()
                .planificacionCompra(planificacion)
                .producto(producto)
                .tipoTela(tipoTela)
                .paletaColor(entrada.getKey().paletaColor())
                .cantidad(entrada.getValue())
                .unidadMedida(tipoTela.isEsPorPeso() ? UnidadMedida.KG : UnidadMedida.UNIDAD)
                .build());
        }
        return detalles;
    }
}
