package com.weclover.backend.service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.weclover.backend.dto.producto.ActualizarColorCierreRequest;
import com.weclover.backend.dto.producto.ActualizarPatronCorteRequest;
import com.weclover.backend.dto.producto.ActualizarTipoTelaRequest;
import com.weclover.backend.dto.producto.MarcarEstadoBanderaRequest;
import com.weclover.backend.dto.producto.ProductoColorItemRequest;
import com.weclover.backend.dto.producto.ProductoColoresRequest;
import com.weclover.backend.dto.producto.ProductoInsumoSecundarioItemRequest;
import com.weclover.backend.dto.producto.ProductoInsumosSecundariosRequest;
import com.weclover.backend.dto.producto.ProductoResponse;
import com.weclover.backend.entity.EstadoBandera;
import com.weclover.backend.entity.MetodoDeteccionColor;
import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.PatronCorte;
import com.weclover.backend.entity.PatronCorteColor;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoColor;
import com.weclover.backend.entity.ProductoInsumoSecundario;
import com.weclover.backend.entity.TipoTela;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.mapper.ProductoMapper;
import com.weclover.backend.repository.PaletaColoresRepository;
import com.weclover.backend.repository.PatronCorteRepository;
import com.weclover.backend.repository.ProductoRepository;
import com.weclover.backend.repository.TipoTelaRepository;
import com.weclover.backend.service.ProductoDisenoValidador.InsumoSugerido;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductoService {

    /**
     * Roles habilitados para cargar/reemplazar/quitar la imagen de diseño de una prenda
     * (ficha técnica) y para asignar los colores reales de cada posición del patrón de
     * corte (modal de gotero, que se dispara justo después de cargar la imagen).
     */
    private static final Set<String> ROLES_CARGA_DISENIO = Set.of(
        "ROLE_ADMINISTRATIVO", "ROLE_VENDEDOR", "ROLE_DISENADOR"
    );

    /** Roles habilitados para marcar el estado de la Bandera (mismo set que habilitaba el
     *  viejo cambio de estado de producto, reusado — ver ProductoEtapaProduccionService). */
    private static final Set<String> ROLES_BANDERA = Set.of("ROLE_ADMINISTRATIVO", "ROLE_PLANTA");

    /** Único tipo de prenda que tiene cierre (ver actualizarColorCierre y el default en asignarColores). */
    private static final String TIPO_PRENDA_CAMPERA = "Campera";

    /** Código estable de la fila "Cierre" en tipos_tela (ver TipoTela.codigo). */
    private static final String CODIGO_TIPO_TELA_CIERRE = "CIERRE";

    /** Descripción fija del insumo Cierre (ver ProductoInsumoSecundario.descripcion: es la
     *  identidad real de la fila, no el tipo de tela). */
    private static final String DESCRIPCION_CIERRE = "Cierre";

    private final ProductoRepository productoRepository;
    private final PaletaColoresRepository paletaColoresRepository;
    private final TipoTelaRepository tipoTelaRepository;
    private final PatronCorteRepository patronCorteRepository;
    private final ProductoMapper productoMapper;
    private final AutorizacionService autorizacionService;
    private final AlmacenamientoImagenService almacenamientoImagenService;
    private final EstadoPedidoService estadoPedidoService;

    @Value("${app.uploads.fichas-tecnicas-dir}")
    private String directorioUploads;

    @Value("${app.uploads.fichas-tecnicas-url-base}")
    private String urlBase;

    @Transactional
    public ProductoResponse subirImagenDiseno(Long idProducto, MultipartFile imagen, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_CARGA_DISENIO);

        Producto producto = productoRepository.findById(idProducto)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el producto con id " + idProducto));

        String url = almacenamientoImagenService.guardar(imagen, directorioUploads, urlBase);
        producto.setImagenDisenoUrl(url);
        Producto guardado = productoRepository.save(producto);
        estadoPedidoService.recalcularEstadoPedido(guardado.getPedido().getId());

        return construirRespuesta(guardado);
    }

    /**
     * Descarta la imagen de diseño recién subida (usado por el botón "Cancelar" del modal
     * de gotero, que abre el frontend automáticamente después de subir la imagen). El
     * archivo en disco no se borra físicamente, mismo criterio ya aceptado para Molderías.
     */
    @Transactional
    public ProductoResponse eliminarImagenDiseno(Long idProducto, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_CARGA_DISENIO);

        Producto producto = productoRepository.findById(idProducto)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el producto con id " + idProducto));

        producto.setImagenDisenoUrl(null);

        return construirRespuesta(productoRepository.save(producto));
    }

    /**
     * Estado de la Bandera (ver EstadoBandera) — solo aplica a productos con tipoPrenda
     * Bandera. No dispara recalcularEstadoPedido: Bandera no afecta TERMINADO, pero el nuevo
     * valor sí queda disponible para la validación de ENTREGADO (ver PedidoService).
     */
    @Transactional
    public ProductoResponse marcarEstadoBandera(Long idProducto, MarcarEstadoBanderaRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_BANDERA);

        Producto producto = productoRepository.findById(idProducto)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el producto con id " + idProducto));

        if (!EtapaProduccionAplicabilidad.esBandera(producto)) {
            throw new BusinessRuleException("Este producto no es Bandera");
        }

        // Se limpian las fechas que ya no corresponden al retroceder de estado (ej. volver a
        // PENDIENTE después de un PEDIDO cargado por error) — sin esto, la Pantalla de
        // Producción mostraba una fecha de "pedido al proveedor" al lado de un estado
        // "Pendiente", inconsistente entre sí.
        EstadoBandera nuevoEstado = request.estadoBandera();
        producto.setEstadoBandera(nuevoEstado);
        switch (nuevoEstado) {
            case PENDIENTE -> {
                producto.setFechaPedidoProveedor(null);
                producto.setFechaRecibido(null);
            }
            case PEDIDO -> {
                producto.setFechaPedidoProveedor(LocalDate.now());
                producto.setFechaRecibido(null);
            }
            case RECIBIDO -> producto.setFechaRecibido(LocalDate.now());
        }

        return construirRespuesta(productoRepository.save(producto));
    }

    /**
     * Asigna (o reemplaza por completo, si ya existía) el color real de cada posición del
     * patrón de corte del producto, cargado a mano con el modal de gotero del frontend.
     */
    @Transactional
    public ProductoResponse asignarColores(Long idProducto, ProductoColoresRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_CARGA_DISENIO);

        Producto producto = productoRepository.findById(idProducto)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el producto con id " + idProducto));

        if (producto.getPatronCorte() == null) {
            throw new BusinessRuleException("El producto no tiene un patrón de corte asignado");
        }

        List<PatronCorteColor> posiciones = producto.getPatronCorte().getColores();
        List<ProductoColorItemRequest> items = request.colores();

        if (items.size() != posiciones.size()) {
            throw new BusinessRuleException(
                "Debe indicar el color de las " + posiciones.size() + " posiciones del patrón de corte");
        }

        Map<Long, PatronCorteColor> posicionesPorId = posiciones.stream()
            .collect(Collectors.toMap(PatronCorteColor::getId, Function.identity()));

        Set<Long> idsRecibidos = new HashSet<>();
        for (ProductoColorItemRequest item : items) {
            if (!posicionesPorId.containsKey(item.idPatronCorteColor())) {
                throw new BusinessRuleException(
                    "La posición " + item.idPatronCorteColor() + " no pertenece al patrón de corte del producto");
            }
            if (!idsRecibidos.add(item.idPatronCorteColor())) {
                throw new BusinessRuleException(
                    "La posición " + item.idPatronCorteColor() + " está duplicada en la solicitud");
            }
        }

        // El flush intermedio es necesario: producto_colores tiene una unique constraint
        // sobre (producto, patronCorteColor) y, si se reemplaza el color de una posición ya
        // asignada, la fila nueva y la vieja comparten esa misma clave. Sin forzar el DELETE
        // de las filas huérfanas antes de insertar las nuevas, el orden de flush por defecto
        // de Hibernate (inserts antes que las eliminaciones de colección) viola la constraint.
        producto.getColores().clear();
        productoRepository.saveAndFlush(producto);

        PaletaColores colorPosicionUno = null;
        for (ProductoColorItemRequest item : items) {
            PaletaColores paletaColor = paletaColoresRepository.findById(item.idPaletaColor())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "No existe el color de paleta con id " + item.idPaletaColor()));

            PatronCorteColor posicion = posicionesPorId.get(item.idPatronCorteColor());
            if (posicion.getOrden() == 1) {
                colorPosicionUno = paletaColor;
            }

            producto.getColores().add(ProductoColor.builder()
                .producto(producto)
                .patronCorteColor(posicion)
                .paletaColor(paletaColor)
                .metodoDeteccion(MetodoDeteccionColor.MANUAL)
                .coordenadaX(item.coordenadaX())
                .coordenadaY(item.coordenadaY())
                .rgbDetectado(item.rgbDetectado())
                .build());
        }

        // Default de insumos secundarios (cierre, capucha en Jersey, puños/cintura en Ribb,
        // según el tipo de prenda): mismo nombre que el "Color 1" recién elegido, buscado
        // dentro de la categoría de tela de cada insumo (no necesariamente la misma fila,
        // porque esa es la de la tela del cuerpo). Si no existe un color con ese nombre
        // exacto en esa categoría, ese insumo queda sin sugerir y hay que completarlo a mano
        // (ver actualizarInsumosSecundarios). Nunca pisa una elección manual ya guardada.
        if (colorPosicionUno != null && producto.getTipoPrenda() != null) {
            List<InsumoSugerido> sugeridos = ProductoDisenoValidador.CODIGOS_INSUMOS_SUGERIDOS_POR_PRENDA
                .getOrDefault(producto.getTipoPrenda().getNombre(), List.of());
            for (InsumoSugerido sugerido : sugeridos) {
                sugerirInsumoSecundario(producto, colorPosicionUno, sugerido.codigoTipoTela(), sugerido.descripcion());
            }
        }

        Producto guardado = productoRepository.save(producto);
        estadoPedidoService.recalcularEstadoPedido(guardado.getPedido().getId());
        return construirRespuesta(guardado);
    }

    /**
     * Moldería (PatronCorte, ver nombre de negocio en el frontend) de esta prenda puntual.
     * Ya no se elige en el alta del pedido (ver ProductoCreateRequest) — se completa después
     * desde Ficha Técnica. idPatronCorte puede ser null para desasignarla. Si la moldería
     * cambia (a otra distinta o a ninguna), se descartan los colores ya marcados: quedaban
     * atados a posiciones (PatronCorteColor) de la moldería anterior, que ya no existen para
     * este producto.
     */
    @Transactional
    public ProductoResponse actualizarPatronCorte(Long idProducto, ActualizarPatronCorteRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_CARGA_DISENIO);

        Producto producto = productoRepository.findById(idProducto)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el producto con id " + idProducto));

        PatronCorte nuevoPatronCorte = null;
        if (request.idPatronCorte() != null) {
            nuevoPatronCorte = patronCorteRepository.findById(request.idPatronCorte())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "No existe el patrón de corte con id " + request.idPatronCorte()));

            if (producto.getTipoPrenda() != null
                    && !nuevoPatronCorte.getTiposPrenda().contains(producto.getTipoPrenda())) {
                throw new BusinessRuleException(
                    "Esta moldería no aplica al tipo de prenda de este producto");
            }
        }

        Long idPatronCorteActual = producto.getPatronCorte() != null ? producto.getPatronCorte().getId() : null;
        Long idPatronCorteNuevo = nuevoPatronCorte != null ? nuevoPatronCorte.getId() : null;
        if (!Objects.equals(idPatronCorteActual, idPatronCorteNuevo)) {
            producto.getColores().clear();
        }

        producto.setPatronCorte(nuevoPatronCorte);

        Producto guardado = productoRepository.save(producto);
        estadoPedidoService.recalcularEstadoPedido(guardado.getPedido().getId());
        return construirRespuesta(guardado);
    }

    /**
     * Tela de esta prenda puntual. Se rechaza cualquier TipoTela con telaCuerpo=false (ej.
     * Cierre, o a futuro Ribb): esas categorías son insumos secundarios, no la tela
     * principal de la prenda.
     */
    @Transactional
    public ProductoResponse actualizarTipoTela(Long idProducto, ActualizarTipoTelaRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_CARGA_DISENIO);

        TipoTela tipoTela = tipoTelaRepository.findByCodigo(request.tipoTela())
            .orElseThrow(() -> new ResourceNotFoundException("No existe el tipo de tela " + request.tipoTela()));

        if (!tipoTela.isTelaCuerpo()) {
            throw new BusinessRuleException(
                tipoTela.getNombre() + " no es un tipo de tela válido para una prenda");
        }

        Producto producto = productoRepository.findById(idProducto)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el producto con id " + idProducto));

        producto.setTipoTela(tipoTela);

        Producto guardado = productoRepository.save(producto);
        estadoPedidoService.recalcularEstadoPedido(guardado.getPedido().getId());
        return construirRespuesta(guardado);
    }

    /** Solo aplica a Camperas (ver TIPO_PRENDA_CAMPERA); el color debe ser de la categoría CIERRE. */
    @Transactional
    public ProductoResponse actualizarColorCierre(Long idProducto, ActualizarColorCierreRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_CARGA_DISENIO);

        Producto producto = productoRepository.findById(idProducto)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el producto con id " + idProducto));

        if (!esCampera(producto)) {
            throw new BusinessRuleException("Esta prenda no tiene cierre");
        }

        PaletaColores color = paletaColoresRepository.findById(request.idPaletaColor())
            .orElseThrow(() -> new ResourceNotFoundException(
                "No existe el color de paleta con id " + request.idPaletaColor()));

        TipoTela cierre = obtenerTipoTelaCierre();
        if (!color.getTipoTela().getId().equals(cierre.getId())) {
            throw new BusinessRuleException("El color elegido no es un color de cierre");
        }

        upsertInsumoSecundario(producto, cierre, DESCRIPCION_CIERRE, color, 1f);

        Producto guardado = productoRepository.save(producto);
        estadoPedidoService.recalcularEstadoPedido(guardado.getPedido().getId());
        return construirRespuesta(guardado);
    }

    /**
     * Reemplaza el set completo de insumos secundarios del producto (capucha, puños y
     * cintura, cierre, o cualquier otro agregado a mano desde el modal) — mismo criterio
     * de "reemplazar todo" que asignarColores usa para los colores del patrón. A propósito
     * sin restricción de qué tipo de prenda puede tener qué insumo (eso lo decide el
     * frontend armando la lista); actualizarColorCierre sigue siendo el único lugar que
     * valida específicamente "solo Campera puede tener Cierre", para no duplicar esa regla
     * acá y mantener ese endpoint funcionando en paralelo sin cambios de comportamiento.
     */
    @Transactional
    public ProductoResponse actualizarInsumosSecundarios(
            Long idProducto, ProductoInsumosSecundariosRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_CARGA_DISENIO);

        Producto producto = productoRepository.findById(idProducto)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el producto con id " + idProducto));

        List<ProductoInsumoSecundarioItemRequest> items = request.insumos();

        // Sin validación de duplicados a propósito: la descripcion puede repetirse (ej. dos
        // "Puños y cintura" de distinto color, uno por puño) — ver ProductoInsumoSecundario.
        // Por eso acá se insertan todas las filas nuevas directamente, sin pasar por
        // upsertInsumoSecundario (que matchea por descripcion): si dos items del pedido
        // comparten descripcion, ese helper actualizaría el primero en vez de agregar un
        // segundo. Mismo problema de flush que asignarColores: sin forzar el DELETE de las
        // filas viejas antes de insertar las nuevas, Hibernate podría violar alguna
        // constraint al reemplazar un insumo ya existente.
        producto.getInsumosSecundarios().clear();
        productoRepository.saveAndFlush(producto);

        for (ProductoInsumoSecundarioItemRequest item : items) {
            TipoTela tipoTela = tipoTelaRepository.findById(item.idTipoTela())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "No existe el tipo de tela con id " + item.idTipoTela()));
            PaletaColores color = paletaColoresRepository.findById(item.idPaletaColor())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "No existe el color de paleta con id " + item.idPaletaColor()));

            if (!color.getTipoTela().getId().equals(tipoTela.getId())) {
                throw new BusinessRuleException(
                    "El color elegido no pertenece al tipo de tela " + tipoTela.getNombre());
            }

            producto.getInsumosSecundarios().add(ProductoInsumoSecundario.builder()
                .producto(producto)
                .descripcion(item.descripcion().trim())
                .tipoTela(tipoTela)
                .color(color)
                .cantidad(item.cantidad())
                .build());
        }

        Producto guardado = productoRepository.save(producto);
        estadoPedidoService.recalcularEstadoPedido(guardado.getPedido().getId());
        return construirRespuesta(guardado);
    }

    /**
     * Elegibilidad de un producto para el Planificador de Compras (Fase 3): delega en
     * ProductoDisenoValidador (ver esa clase para el detalle del chequeo). Método público
     * mantenido acá porque PlanificacionCompraService ya depende de este servicio para
     * construirRespuesta — evita agregar una dependencia nueva solo para esto.
     */
    public Optional<String> motivoDisenoIncompleto(Producto producto) {
        return ProductoDisenoValidador.motivoDisenoIncompleto(producto);
    }

    private boolean esCampera(Producto producto) {
        return producto.getTipoPrenda() != null
            && TIPO_PRENDA_CAMPERA.equalsIgnoreCase(producto.getTipoPrenda().getNombre());
    }

    private TipoTela obtenerTipoTelaCierre() {
        return tipoTelaRepository.findByCodigo(CODIGO_TIPO_TELA_CIERRE)
            .orElseThrow(() -> new ResourceNotFoundException(
                "No existe el tipo de tela " + CODIGO_TIPO_TELA_CIERRE + " en el catálogo"));
    }

    private Optional<ProductoInsumoSecundario> obtenerInsumoCierre(Producto producto) {
        return producto.getInsumosSecundarios().stream()
            .filter(insumo -> DESCRIPCION_CIERRE.equals(insumo.getDescripcion()))
            .findFirst();
    }

    /**
     * Sugiere un insumo secundario con descripcion=descripcion (ej. "Capucha") y
     * tipoTela=codigoTipoTela, con el mismo nombre de color que colorPosicionUno, buscado en
     * esa categoría de tela. No hace nada si: el tipo de tela no existe en el catálogo, ya
     * hay un insumo con esa descripcion para el producto (no pisa una elección manual — se
     * matchea por descripcion, no por tipo de tela, ver ProductoInsumoSecundario), o no hay
     * un color con ese nombre exacto en esa categoría (queda para completar a mano). La
     * cantidad sugerida sale de TipoTela.gramosSugerido si es por peso, o 1 si es por unidad
     * (ej. Cierre) — mismo criterio 100% de datos, sin hardcodear cantidades por tipo acá.
     */
    private void sugerirInsumoSecundario(Producto producto, PaletaColores colorPosicionUno, String codigoTipoTela, String descripcion) {
        Optional<TipoTela> tipoTelaOpt = tipoTelaRepository.findByCodigo(codigoTipoTela);
        if (tipoTelaOpt.isEmpty()) {
            return;
        }
        TipoTela tipoTela = tipoTelaOpt.get();

        boolean yaExiste = producto.getInsumosSecundarios().stream()
            .anyMatch(insumo -> descripcion.equals(insumo.getDescripcion()));
        if (yaExiste) {
            return;
        }

        paletaColoresRepository.findByNombreIgnoreCaseAndTipoTela(colorPosicionUno.getNombre(), tipoTela)
            .ifPresent(color -> {
                float cantidad = tipoTela.isEsPorPeso()
                    ? (tipoTela.getGramosSugerido() != null ? tipoTela.getGramosSugerido() : 0f)
                    : 1f;
                upsertInsumoSecundario(producto, tipoTela, descripcion, color, cantidad);
            });
    }

    /** Upsert por (producto, descripcion): si ya había una fila con esa descripcion, la
     *  reemplaza (incluido el tipoTela, que puede cambiar) en vez de duplicar. */
    private void upsertInsumoSecundario(Producto producto, TipoTela tipoTela, String descripcion, PaletaColores color, float cantidad) {
        Optional<ProductoInsumoSecundario> existente = producto.getInsumosSecundarios().stream()
            .filter(insumo -> descripcion.equals(insumo.getDescripcion()))
            .findFirst();

        if (existente.isPresent()) {
            existente.get().setTipoTela(tipoTela);
            existente.get().setColor(color);
            existente.get().setCantidad(cantidad);
        } else {
            producto.getInsumosSecundarios().add(ProductoInsumoSecundario.builder()
                .producto(producto)
                .descripcion(descripcion)
                .tipoTela(tipoTela)
                .color(color)
                .cantidad(cantidad)
                .build());
        }
    }

    /**
     * Envoltorio de productoMapper.toResponse() que completa idColorCierre/nombreColorCierre/
     * hexColorCierre a partir de ProductoInsumoSecundario (ya no son un campo directo de
     * Producto), para mantener exactamente el mismo contrato de ProductoResponse que existía
     * con el viejo Producto.colorCierre. Público porque PedidoService también lo necesita:
     * PedidoMapper arma PedidoResponse.productos vía ProductoMapper directo (MapStruct), que
     * por sí solo no sabe completar estos tres campos derivados.
     */
    public ProductoResponse construirRespuesta(Producto producto) {
        ProductoResponse base = productoMapper.toResponse(producto);
        ProductoInsumoSecundario cierre = obtenerInsumoCierre(producto).orElse(null);

        return new ProductoResponse(
            base.id(),
            base.idTipoPrenda(),
            base.tipoPrenda(),
            base.idPatronCorte(),
            base.patronCorteColores(),
            base.tipoTela(),
            cierre != null ? cierre.getColor().getId() : null,
            cierre != null ? cierre.getColor().getNombre() : null,
            cierre != null ? cierre.getColor().getHex() : null,
            base.cantidadTotal(),
            base.costo(),
            base.subtotal(),
            base.observaciones(),
            base.imagenDisenoUrl(),
            base.estadoBandera(),
            base.fechaPedidoProveedor(),
            base.fechaRecibido(),
            base.colores(),
            base.insumosSecundarios()
        );
    }
}
