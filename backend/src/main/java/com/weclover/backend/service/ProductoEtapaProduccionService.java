package com.weclover.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.producto.EtapaProduccionResponse;
import com.weclover.backend.dto.producto.EtapaUpdateDTO;
import com.weclover.backend.dto.producto.ProductoEtapasResponse;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.EstadoProduccion;
import com.weclover.backend.entity.EtapaProduccion;
import com.weclover.backend.entity.HistorialEstadoPedido;
import com.weclover.backend.entity.MovimientoEstado;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoEtapaProduccion;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.repository.MovimientoEstadoRepository;
import com.weclover.backend.repository.ProductoEtapaProduccionRepository;
import com.weclover.backend.repository.ProductoRepository;
import com.weclover.backend.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Seguimiento de producción por etapa de productos no-Bandera (ver ProductoEtapaProduccion).
 * Mismo rol que hoy habilita cambiar el estado de un Producto (ROLE_PLANTA), reusado tal cual
 * — reemplaza al viejo mecanismo de Producto.estadoActual + ProductoService.cambiarEstado.
 */
@Service
@RequiredArgsConstructor
public class ProductoEtapaProduccionService {

    private static final Set<String> ROLES_ETAPAS = Set.of("ROLE_ADMINISTRATIVO", "ROLE_PLANTA");

    static final String OBSERVACION_RECALCULO = "Recalculado por cambio en las etapas aplicables del producto";
    static final String OBSERVACION_RETROACTIVA = "Carga retroactiva";
    static final String OBSERVACION_BAJA = "Prenda eliminada del pedido";

    /** Etiqueta de la última etapa completada, en orden de pipeline (ver 3.3). CONTROL no
     *  tiene entrada propia: si está completa, ya se cubrió con "todas aplicables completas". */
    private static final Map<EtapaProduccion, String> LABEL_POR_ETAPA = new LinkedHashMap<>();
    static {
        LABEL_POR_ETAPA.put(EtapaProduccion.CORTE, "CORTADO");
        LABEL_POR_ETAPA.put(EtapaProduccion.ESTAMPADO, "ESTAMPADO");
        LABEL_POR_ETAPA.put(EtapaProduccion.BORDADO, "BORDADO");
        LABEL_POR_ETAPA.put(EtapaProduccion.CONFECCION, "CONFECCIONADO");
        LABEL_POR_ETAPA.put(EtapaProduccion.APODO, "APODADO");
        LABEL_POR_ETAPA.put(EtapaProduccion.OJAL, "OJALADO");
    }

    private final ProductoEtapaProduccionRepository productoEtapaProduccionRepository;
    private final MovimientoEstadoRepository movimientoEstadoRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AutorizacionService autorizacionService;
    private final EstadoPedidoService estadoPedidoService;

    /** Genera las filas de etapas aplicables para un producto no-Bandera recién creado, o
     *  agrega las que falten si la aplicabilidad cambió (ej. se agregó un insumo "Estampado"
     *  después de creado el producto) — idempotente, nunca borra ni pisa una fila existente.
     *  También recalcula Producto.estadoProduccion: cualquier cambio de aplicabilidad (tipo de
     *  prenda, insumo "Estampado") pasa por acá, así que el estado persistido no queda viejo. */
    @Transactional
    public void sincronizarEtapas(Producto producto) {
        if (EtapaProduccionAplicabilidad.esBandera(producto)) {
            if (producto.getEstadoProduccion() != null) {
                // Pasó a ser Bandera: deja de participar de producción (sin movimiento, no hay
                // un estado "nuevo" que registrar).
                producto.setEstadoProduccion(null);
                productoRepository.save(producto);
            }
            return;
        }
        agregarEtapasFaltantes(producto);
        recalcularEstadoProduccion(producto, null, OBSERVACION_RECALCULO);
        productoRepository.save(producto);
    }

    /**
     * Único punto que escribe Producto.estadoProduccion (fuera de la carga retroactiva). Si el
     * estado calculado cambia, registra un MovimientoEstado sin etapa en la misma transacción.
     * La primera asignación (estado previo null = producto recién creado) no genera movimiento.
     * Lo llaman sincronizarEtapas (cambios de aplicabilidad) y PedidoService al marcar un pedido
     * como ENTREGADO o sacarlo de ENTREGADO (con el actor real). Los marcados de etapa no pasan
     * por acá: registran su propio movimiento con etapa (ver aplicarMarcado).
     */
    @Transactional
    public void recalcularEstadoProduccion(Producto producto, Usuario actor, String observaciones) {
        EstadoProduccion anterior = producto.getEstadoProduccion();
        EstadoProduccion nuevo = EstadoProduccionCalculador.calcular(producto);
        if (Objects.equals(anterior, nuevo)) {
            return;
        }
        producto.setEstadoProduccion(nuevo);
        if (anterior == null || nuevo == null) {
            return;
        }
        movimientoEstadoRepository.save(MovimientoEstado.builder()
            .producto(producto)
            .estadoAnterior(anterior)
            .estadoNuevo(nuevo)
            .fechaHora(LocalDateTime.now())
            .usuario(actor)
            .unidades(producto.getCantidadTotal())
            .observaciones(observaciones)
            .build());
    }

    /**
     * Baja lógica de una prenda quitada del pedido: se marca habilitado=false (no se borra la
     * fila, para conservar su historial) y se deja un MovimientoEstado sin etapa cuya fechaHora
     * es la fecha de la baja. estadoNuevo queda null: la prenda deja de tener estado vigente
     * (Producto.estadoProduccion se conserva como "último estado conocido").
     */
    @Transactional
    public void darDeBaja(Producto producto, Usuario actor) {
        producto.setHabilitado(false);
        movimientoEstadoRepository.save(MovimientoEstado.builder()
            .producto(producto)
            .estadoAnterior(producto.getEstadoProduccion())
            .fechaHora(LocalDateTime.now())
            .usuario(actor)
            .unidades(producto.getCantidadTotal())
            .observaciones(OBSERVACION_BAJA)
            .build());
        productoRepository.save(producto);
    }

    /**
     * Carga retroactiva de Producto.estadoProduccion para productos no-Bandera que todavía no lo
     * tienen (existentes antes de esta columna). Idempotente: solo toca productos con estado
     * null, así que después de la primera corrida no hace nada. Además de setear el estado,
     * inserta movimientos "Carga retroactiva" (usuario null) para que el reporte "terminadas por
     * mes" tenga historia: TERMINADO fechado con la última fechaCompletado de sus etapas, y
     * ENTREGADO fechado con la última entrada del pedido a ENTREGADO en HistorialEstadoPedido.
     *
     * @return cantidad de productos inicializados
     */
    @Transactional
    public int inicializarEstadosFaltantes() {
        int inicializados = 0;
        for (Producto producto : productoRepository.findByEstadoProduccionIsNullAndHabilitadoTrue()) {
            if (EtapaProduccionAplicabilidad.esBandera(producto)) {
                continue;
            }
            agregarEtapasFaltantes(producto);
            EstadoProduccion estado = EstadoProduccionCalculador.calcular(producto);
            producto.setEstadoProduccion(estado);
            productoRepository.save(producto);
            inicializados++;

            EstadoProduccion anterior = null;
            if (todasLasEtapasAplicablesCompletas(producto)) {
                LocalDateTime fechaTerminado = producto.getEtapas().stream()
                    .map(ProductoEtapaProduccion::getFechaCompletado)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .map(LocalDate::atStartOfDay)
                    .orElse(LocalDateTime.now());
                guardarMovimientoRetroactivo(producto, null, EstadoProduccion.TERMINADO, fechaTerminado);
                anterior = EstadoProduccion.TERMINADO;
            }
            if (estado == EstadoProduccion.ENTREGADO) {
                LocalDateTime fechaEntrega = producto.getPedido().getHistorial().stream()
                    .filter(h -> h.getEstado() == EstadoPedido.ENTREGADO)
                    .map(HistorialEstadoPedido::getFechaCambio)
                    .max(Comparator.naturalOrder())
                    .orElse(LocalDateTime.now());
                guardarMovimientoRetroactivo(producto, anterior, EstadoProduccion.ENTREGADO, fechaEntrega);
            }
        }
        return inicializados;
    }

    private void guardarMovimientoRetroactivo(
            Producto producto, EstadoProduccion anterior, EstadoProduccion nuevo, LocalDateTime fecha) {
        movimientoEstadoRepository.save(MovimientoEstado.builder()
            .producto(producto)
            .estadoAnterior(anterior)
            .estadoNuevo(nuevo)
            .fechaHora(fecha)
            .unidades(producto.getCantidadTotal())
            .observaciones(OBSERVACION_RETROACTIVA)
            .build());
    }

    /** Alta de las filas de etapas aplicables que falten. Separado de sincronizarEtapas porque la
     *  carga retroactiva necesita las filas sin el recálculo (asigna el estado ella misma, con
     *  movimientos fechados en el pasado, no "ahora"). */
    private void agregarEtapasFaltantes(Producto producto) {
        Set<EtapaProduccion> yaCreadas = producto.getEtapas().stream()
            .map(ProductoEtapaProduccion::getEtapa)
            .collect(Collectors.toSet());
        for (EtapaProduccion etapa : EtapaProduccionAplicabilidad.etapasAplicables(producto)) {
            if (!yaCreadas.contains(etapa)) {
                producto.getEtapas().add(ProductoEtapaProduccion.builder()
                    .producto(producto)
                    .etapa(etapa)
                    .completado(false)
                    .build());
            }
        }
    }

    private boolean todasLasEtapasAplicablesCompletas(Producto producto) {
        Set<EtapaProduccion> completas = producto.getEtapas().stream()
            .filter(ProductoEtapaProduccion::isCompletado)
            .map(ProductoEtapaProduccion::getEtapa)
            .collect(Collectors.toSet());
        return completas.containsAll(EtapaProduccionAplicabilidad.etapasAplicables(producto));
    }

    @Transactional
    public ProductoEtapasResponse marcarEtapa(
            Long idProducto, EtapaProduccion etapa, boolean completado, Long idEmpleado, String comentario,
            Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_ETAPAS);

        Producto producto = obtenerProducto(idProducto);
        if (EtapaProduccionAplicabilidad.esBandera(producto)) {
            throw new BusinessRuleException("Este producto es Bandera, no usa etapas de producción");
        }
        sincronizarEtapas(producto);

        if (!EtapaProduccionAplicabilidad.etapasAplicables(producto).contains(etapa)) {
            throw new BusinessRuleException("La etapa " + etapa + " no aplica a este producto");
        }

        ProductoEtapaProduccion fila = productoEtapaProduccionRepository.findByProductoAndEtapa(producto, etapa)
            .orElseThrow(() -> new ResourceNotFoundException("No existe la etapa " + etapa + " para este producto"));

        aplicarMarcado(fila, completado, idEmpleado, comentario, idUsuarioActor);
        productoEtapaProduccionRepository.save(fila);

        estadoPedidoService.recalcularEstadoPedido(producto.getPedido().getId());

        return construirRespuesta(producto);
    }

    @Transactional
    public void marcarEtapasBulk(List<EtapaUpdateDTO> items, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_ETAPAS);

        Set<Long> idsPedidosAfectados = new LinkedHashSet<>();

        for (EtapaUpdateDTO item : items) {
            Producto producto = obtenerProducto(item.idProducto());
            if (EtapaProduccionAplicabilidad.esBandera(producto)) {
                throw new BusinessRuleException(
                    "El producto #" + producto.getId() + " es Bandera, no usa etapas de producción");
            }
            sincronizarEtapas(producto);

            if (!EtapaProduccionAplicabilidad.etapasAplicables(producto).contains(item.etapa())) {
                throw new BusinessRuleException(
                    "La etapa " + item.etapa() + " no aplica al producto #" + producto.getId());
            }

            ProductoEtapaProduccion fila = productoEtapaProduccionRepository
                .findByProductoAndEtapa(producto, item.etapa())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "No existe la etapa " + item.etapa() + " para el producto #" + producto.getId()));

            aplicarMarcado(fila, item.completado(), item.idEmpleado(), item.comentario(), idUsuarioActor);
            productoEtapaProduccionRepository.save(fila);
            idsPedidosAfectados.add(producto.getPedido().getId());
        }

        idsPedidosAfectados.forEach(estadoPedidoService::recalcularEstadoPedido);
    }

    /** No es readOnly a pesar de ser una consulta: sincronizarEtapas puede insertar filas
     *  faltantes (ver esa función) — con readOnly=true la conexión JDBC rechaza ese insert. */
    @Transactional
    public ProductoEtapasResponse listarEtapas(Long idProducto, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_ETAPAS);

        Producto producto = obtenerProducto(idProducto);
        if (!EtapaProduccionAplicabilidad.esBandera(producto)) {
            sincronizarEtapas(producto);
        }
        return construirRespuesta(producto);
    }

    /** Además de actualizar el valor actual (ProductoEtapaProduccion), recalcula
     *  Producto.estadoProduccion y agrega una fila nueva a MovimientoEstado con la etapa y el
     *  estado anterior/nuevo — "cada cambio en producción" queda registrado, sin importar si
     *  el valor efectivamente cambió (misma llamada puede reasignar el empleado sin tocar
     *  completado, por ejemplo, y eso también es un cambio que auditar). El comentario
     *  opcional de quien marca queda en MovimientoEstado.observaciones. */
    private void aplicarMarcado(
            ProductoEtapaProduccion fila, boolean completado, Long idEmpleado, String comentario, Long idUsuarioActor) {
        fila.setCompletado(completado);
        fila.setFechaCompletado(completado ? LocalDate.now() : null);
        if (idEmpleado != null) {
            Usuario empleado = usuarioRepository.findById(idEmpleado)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el usuario empleado con id " + idEmpleado));
            fila.setEmpleado(empleado);
        } else if (!completado) {
            fila.setEmpleado(null);
        }

        Usuario actor = usuarioRepository.findById(idUsuarioActor)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el usuario que realiza el cambio"));

        Producto producto = fila.getProducto();
        EstadoProduccion anterior = producto.getEstadoProduccion();
        EstadoProduccion nuevo = EstadoProduccionCalculador.calcular(producto);
        producto.setEstadoProduccion(nuevo);

        movimientoEstadoRepository.save(MovimientoEstado.builder()
            .producto(producto)
            .etapa(fila.getEtapa())
            .completado(completado)
            .empleado(fila.getEmpleado())
            .estadoAnterior(anterior)
            .estadoNuevo(nuevo)
            .fechaHora(LocalDateTime.now())
            .usuario(actor)
            .unidades(producto.getCantidadTotal())
            .observaciones(comentario == null || comentario.isBlank() ? null : comentario.trim())
            .build());
    }

    /** Todas las etapas aplicables de este producto no-Bandera están completadas (incluye
     *  CONTROL) — usado por EstadoPedidoService para la condición de TERMINADO (3.2). */
    public boolean todasLasEtapasCompletas(Producto producto) {
        sincronizarEtapas(producto);
        List<EtapaProduccion> aplicables = EtapaProduccionAplicabilidad.etapasAplicables(producto);
        List<ProductoEtapaProduccion> filas = productoEtapaProduccionRepository.findByProducto(producto);

        Map<EtapaProduccion, Boolean> completadoPorEtapa = filas.stream()
            .collect(Collectors.toMap(ProductoEtapaProduccion::getEtapa, ProductoEtapaProduccion::isCompletado));

        return aplicables.stream().allMatch(etapa -> Boolean.TRUE.equals(completadoPorEtapa.get(etapa)));
    }

    private ProductoEtapasResponse construirRespuesta(Producto producto) {
        List<ProductoEtapaProduccion> filas = productoEtapaProduccionRepository.findByProducto(producto);

        List<EtapaProduccionResponse> etapasResponse = filas.stream()
            .sorted(Comparator.comparingInt(fila -> fila.getEtapa().ordinal()))
            .map(fila -> new EtapaProduccionResponse(
                fila.getEtapa(),
                fila.isCompletado(),
                fila.getFechaCompletado(),
                fila.getEmpleado() != null ? fila.getEmpleado().getId() : null,
                fila.getEmpleado() != null ? fila.getEmpleado().getNombre() : null
            ))
            .toList();

        return new ProductoEtapasResponse(producto.getId(), etapasResponse, calcularEstadoVisual(filas));
    }

    /** Estado visible del producto (3.3): recorre las etapas aplicables en orden de pipeline y
     *  toma el label de la de mayor orden completada. PENDIENTE si ninguna está completa;
     *  TERMINADO si todas las aplicables (incluida CONTROL) lo están. Público porque
     *  ProduccionService lo reusa para armar la grilla de la Pantalla de Producción. */
    public String calcularEstadoVisual(List<ProductoEtapaProduccion> filas) {
        Map<EtapaProduccion, Boolean> completadoPorEtapa = filas.stream()
            .collect(Collectors.toMap(ProductoEtapaProduccion::getEtapa, ProductoEtapaProduccion::isCompletado));

        boolean todasCompletas = !filas.isEmpty() && filas.stream().allMatch(ProductoEtapaProduccion::isCompletado);
        if (todasCompletas) {
            return "TERMINADO";
        }

        String label = "PENDIENTE";
        for (Map.Entry<EtapaProduccion, String> entrada : LABEL_POR_ETAPA.entrySet()) {
            if (Boolean.TRUE.equals(completadoPorEtapa.get(entrada.getKey()))) {
                label = entrada.getValue();
            }
        }
        return label;
    }

    /** Un producto dado de baja (habilitado=false) se trata como inexistente. */
    private Producto obtenerProducto(Long idProducto) {
        return productoRepository.findById(idProducto)
            .filter(Producto::isHabilitado)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el producto con id " + idProducto));
    }
}
