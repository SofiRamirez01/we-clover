package com.weclover.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.cargatalles.AlumnoCreateRequest;
import com.weclover.backend.dto.cargatalles.AlumnoResponse;
import com.weclover.backend.dto.cargatalles.CargaTallesResponse;
import com.weclover.backend.dto.cargatalles.ComboResponse;
import com.weclover.backend.dto.cargatalles.ComboUpsertRequest;
import com.weclover.backend.dto.cargatalles.FilaTablaTalleResponse;
import com.weclover.backend.dto.cargatalles.GrupoTallaResponse;
import com.weclover.backend.dto.cargatalles.LinkCargaTallesResponse;
import com.weclover.backend.dto.cargatalles.ProductoPedidoResumenResponse;
import com.weclover.backend.entity.AlumnoPedido;
import com.weclover.backend.entity.AlumnoProductoTalle;
import com.weclover.backend.entity.CargaTallesPedido;
import com.weclover.backend.entity.EstadoCargaTalles;
import com.weclover.backend.entity.GrupoTalle;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.TablaTalle;
import com.weclover.backend.entity.TipoPrenda;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.repository.AlumnoPedidoRepository;
import com.weclover.backend.repository.AlumnoProductoTalleRepository;
import com.weclover.backend.repository.CargaTallesPedidoRepository;
import com.weclover.backend.repository.PedidoRepository;
import com.weclover.backend.repository.ProductoRepository;
import com.weclover.backend.repository.TablaTalleRepository;
import com.weclover.backend.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Link público de carga de talles por Pedido (sin login, protegido solo por `token`) más las
 * acciones internas de generar/cerrar/reabrir/consultar (ROLE_VENDEDOR/ROLE_ADMINISTRATIVO). No
 * hay filtro de seguridad HTTP en este proyecto todavía (ver doc/pantallas-pendientes.md) — acá
 * "protegido por token" es literal: los métodos públicos no reciben ni exigen idUsuarioActor,
 * solo validan que el token exista y que el estado sea ABIERTO donde corresponda.
 */
@Service
@RequiredArgsConstructor
public class CargaTallesService {

    private static final Set<String> ROLES_CARGA_TALLES = Set.of("ROLE_ADMINISTRATIVO", "ROLE_VENDEDOR");

    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CargaTallesPedidoRepository cargaTallesPedidoRepository;
    private final AlumnoPedidoRepository alumnoPedidoRepository;
    private final AlumnoProductoTalleRepository alumnoProductoTalleRepository;
    private final TablaTalleRepository tablaTalleRepository;
    private final AutorizacionService autorizacionService;

    // ---------- Internas ----------

    /** Se llama automáticamente al crear un pedido (ver PedidoService.crearPedido) para que el
     *  link ya exista desde el alta y nadie tenga que generarlo a mano. Sin chequeo de rol: quien
     *  ya pudo crear el pedido no necesita revalidar acá. */
    @Transactional
    public void generarLinkParaPedidoNuevo(Pedido pedido) {
        obtenerOCrearCargaDePedido(pedido);
    }

    @Transactional
    public LinkCargaTallesResponse cerrar(Long idPedido, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_CARGA_TALLES);
        CargaTallesPedido carga = obtenerCargaDePedido(idPedido);
        carga.setEstado(EstadoCargaTalles.CERRADO);
        carga.setFechaCierre(LocalDateTime.now());
        carga.setCerradoPor(obtenerUsuario(idUsuarioActor));
        return aLinkResponse(cargaTallesPedidoRepository.save(carga));
    }

    @Transactional
    public LinkCargaTallesResponse reabrir(Long idPedido, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_CARGA_TALLES);
        CargaTallesPedido carga = obtenerCargaDePedido(idPedido);
        carga.setEstado(EstadoCargaTalles.ABIERTO);
        carga.setFechaCierre(null);
        carga.setCerradoPor(null);
        return aLinkResponse(cargaTallesPedidoRepository.save(carga));
    }

    /** Sin chequeo de rol — Ficha Técnica (donde vive esto ahora) ya es visible para cualquier
     *  rol, así que el link/conteo de talles acompaña a esa misma visibilidad. Si el pedido es
     *  viejo y todavía no tiene carga de talles (de antes de que se generara automáticamente al
     *  crear el pedido), se crea acá mismo la primera vez que se consulta. */
    @Transactional
    public CargaTallesResponse obtenerInterno(Long idPedido) {
        Pedido pedido = obtenerPedido(idPedido);
        return construirRespuesta(obtenerOCrearCargaDePedido(pedido));
    }

    // ---------- Públicas (sin login, por token) ----------

    @Transactional(readOnly = true)
    public CargaTallesResponse obtenerPorToken(String token) {
        return construirRespuesta(obtenerCargaPorToken(token));
    }

    @Transactional
    public AlumnoResponse agregarAlumno(String token, AlumnoCreateRequest request) {
        CargaTallesPedido carga = obtenerCargaAbiertaPorToken(token);
        int orden = alumnoPedidoRepository.countByCargaTalles(carga);
        AlumnoPedido alumno = alumnoPedidoRepository.save(AlumnoPedido.builder()
            .cargaTalles(carga)
            .nombreAlumno(request.nombreAlumno().trim())
            .orden(orden)
            .build());
        return new AlumnoResponse(alumno.getId(), alumno.getNombreAlumno(), alumno.getOrden(), List.of());
    }

    /** Borra un alumno entero con todos sus combos — es la forma de corregir una composición
     *  mal cargada al alta (ya no hay botón para sumar/restar unidades sueltas después: si se
     *  equivocaron, se borra el alumno y se vuelve a agregar con la composición correcta). */
    @Transactional
    public void eliminarAlumno(String token, Long idAlumno) {
        CargaTallesPedido carga = obtenerCargaAbiertaPorToken(token);
        AlumnoPedido alumno = obtenerAlumnoDeCarga(idAlumno, carga);
        alumnoProductoTalleRepository.deleteAll(alumnoProductoTalleRepository.findByAlumnoPedido(alumno));
        alumnoPedidoRepository.delete(alumno);
    }

    /** Agrega una unidad nueva (un "combo") de un Producto para un alumno — sin medida
     *  todavía. Puede llamarse varias veces para el mismo (alumno, producto): cada llamada es
     *  una unidad más (ej. dos remeras para el mismo alumno), cada una con su propia medida. */
    @Transactional
    public ComboResponse agregarUnidad(String token, Long idAlumno, Long idProducto) {
        CargaTallesPedido carga = obtenerCargaAbiertaPorToken(token);
        AlumnoPedido alumno = obtenerAlumnoDeCarga(idAlumno, carga);
        Producto producto = obtenerProductoDelPedido(idProducto, carga.getPedido());
        if (producto.getTipoPrenda() == null || producto.getTipoPrenda().getGrupoTalle() == null) {
            throw new BusinessRuleException("Esta prenda no usa talles, no se le puede cargar una medida");
        }
        // Repetido acá aunque el front ya limita el +/- a lo restante: esta ruta es pública y sin
        // login, así que dos representantes de curso podrían estar cargando al mismo tiempo.
        if (alumnoProductoTalleRepository.countByProducto(producto) >= producto.getCantidadTotal()) {
            throw new BusinessRuleException(
                "Ya se asignaron todas las unidades de \"" + producto.getTipoPrenda().getNombre() + "\" del pedido");
        }
        AlumnoProductoTalle unidad = alumnoProductoTalleRepository.save(AlumnoProductoTalle.builder()
            .alumnoPedido(alumno)
            .producto(producto)
            .personalizado(false)
            .build());
        return aComboResponse(unidad);
    }

    /** Cierra la carga desde el link público — la usa el representante de curso cuando ya
     *  terminó de cargar todo (ver el botón "Finalizar" en CargaTallesPublicaView). Vuelve a
     *  validar que esté todo completo acá (no solo confiar en el botón deshabilitado del
     *  front), y queda `cerradoPor = null` para distinguirlo de un cierre hecho por
     *  Vendedor/Administrativo desde Ficha Técnica — que sigue pudiendo reabrir/cerrar esto
     *  igual que antes, sin cambios. */
    @Transactional
    public void finalizarPorRepresentante(String token) {
        CargaTallesPedido carga = obtenerCargaAbiertaPorToken(token);
        validarTallesCompletos(carga);
        carga.setEstado(EstadoCargaTalles.CERRADO);
        carga.setFechaCierre(LocalDateTime.now());
        carga.setCerradoPor(null);
        cargaTallesPedidoRepository.save(carga);
    }

    @Transactional
    public ComboResponse actualizarCombo(String token, Long idCombo, ComboUpsertRequest request) {
        CargaTallesPedido carga = obtenerCargaAbiertaPorToken(token);
        AlumnoProductoTalle combo = obtenerComboDeCarga(idCombo, carga);

        combo.setAnchoCm(request.anchoCm());
        combo.setLargoCm(request.largoCm());
        combo.setObservacionPersonalizado(request.observacionPersonalizado());

        if (request.anchoCm() != null && request.largoCm() != null) {
            GrupoTalle grupo = combo.getProducto().getTipoPrenda().getGrupoTalle();
            Optional<TablaTalle> talle = calcularTalle(grupo, request.anchoCm(), request.largoCm());
            combo.setTalleAsignado(talle.orElse(null));
            combo.setPersonalizado(talle.isEmpty());
        } else {
            combo.setTalleAsignado(null);
            combo.setPersonalizado(false);
        }

        return aComboResponse(alumnoProductoTalleRepository.save(combo));
    }

    // ---------- Cálculo de talle (CAMBIO 4) ----------

    /** Recorre la tabla del grupo en orden ascendente y toma la primera fila cuyo ancho Y largo
     *  sean mayores o iguales a la medida ingresada — sin margen de tolerancia. Vacío si
     *  ninguna fila cubre ambas dimensiones a la vez (talle personalizado). */
    private Optional<TablaTalle> calcularTalle(GrupoTalle grupo, int anchoCm, int largoCm) {
        return tablaTalleRepository.findByGrupoTalleOrderByOrdenAsc(grupo).stream()
            .filter(fila -> fila.getAnchoCm() >= anchoCm && fila.getLargoCm() >= largoCm)
            .findFirst();
    }

    // ---------- Helpers de lookup ----------

    private Pedido obtenerPedido(Long id) {
        return pedidoRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el pedido con id " + id));
    }

    private Usuario obtenerUsuario(Long id) {
        return usuarioRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el usuario con id " + id));
    }

    private CargaTallesPedido obtenerCargaDePedido(Long idPedido) {
        Pedido pedido = obtenerPedido(idPedido);
        return cargaTallesPedidoRepository.findByPedido(pedido)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Este pedido todavía no tiene un link de carga de talles generado"));
    }

    private CargaTallesPedido obtenerOCrearCargaDePedido(Pedido pedido) {
        return cargaTallesPedidoRepository.findByPedido(pedido)
            .orElseGet(() -> cargaTallesPedidoRepository.save(CargaTallesPedido.builder()
                .pedido(pedido)
                .token(UUID.randomUUID().toString())
                .estado(EstadoCargaTalles.ABIERTO)
                .fechaCreacion(LocalDateTime.now())
                .build()));
    }

    private CargaTallesPedido obtenerCargaPorToken(String token) {
        return cargaTallesPedidoRepository.findByToken(token)
            .orElseThrow(() -> new ResourceNotFoundException("El link de carga de talles no existe o ya no es válido"));
    }

    private CargaTallesPedido obtenerCargaAbiertaPorToken(String token) {
        CargaTallesPedido carga = obtenerCargaPorToken(token);
        if (carga.getEstado() != EstadoCargaTalles.ABIERTO) {
            throw new BusinessRuleException("La carga de talles ya fue cerrada");
        }
        return carga;
    }

    private AlumnoPedido obtenerAlumnoDeCarga(Long idAlumno, CargaTallesPedido carga) {
        AlumnoPedido alumno = alumnoPedidoRepository.findById(idAlumno)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el alumno con id " + idAlumno));
        if (!alumno.getCargaTalles().getId().equals(carga.getId())) {
            throw new ResourceNotFoundException("Ese alumno no pertenece a esta carga de talles");
        }
        return alumno;
    }

    private Producto obtenerProductoDelPedido(Long idProducto, Pedido pedido) {
        Producto producto = productoRepository.findById(idProducto)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el producto con id " + idProducto));
        if (!producto.getPedido().getId().equals(pedido.getId())) {
            throw new ResourceNotFoundException("Ese producto no pertenece a este pedido");
        }
        return producto;
    }

    private AlumnoProductoTalle obtenerComboDeCarga(Long idCombo, CargaTallesPedido carga) {
        return alumnoProductoTalleRepository.findByIdAndAlumnoPedido_CargaTalles(idCombo, carga)
            .orElseThrow(() -> new ResourceNotFoundException("No existe ese combo en esta carga de talles"));
    }

    /** "Completo" para un Producto con talle es: se asignaron exactamente las unidades del
     *  pedido (ni de más — eso ya lo impide agregarUnidad — ni de menos) Y todas esas unidades
     *  ya tienen ancho/largo cargados. `cantidadCargada` del resumen cuenta unidades asignadas
     *  sin importar si están medidas, así que acá hace falta mirar los combos uno por uno. */
    private void validarTallesCompletos(CargaTallesPedido carga) {
        List<AlumnoProductoTalle> todosLosCombos = alumnoProductoTalleRepository.findByAlumnoPedido_CargaTalles(carga);
        Map<Long, List<AlumnoProductoTalle>> combosPorProducto = todosLosCombos.stream()
            .collect(Collectors.groupingBy(c -> c.getProducto().getId()));

        for (Producto producto : carga.getPedido().getProductos()) {
            if (producto.getTipoPrenda() == null || producto.getTipoPrenda().getGrupoTalle() == null) {
                continue;
            }
            List<AlumnoProductoTalle> combos = combosPorProducto.getOrDefault(producto.getId(), List.of());
            boolean completo = combos.size() == producto.getCantidadTotal()
                && combos.stream().allMatch(c -> c.getAnchoCm() != null && c.getLargoCm() != null);
            if (!completo) {
                throw new BusinessRuleException(
                    "Todavía faltan medidas de \"" + producto.getTipoPrenda().getNombre() + "\" para poder finalizar");
            }
        }
    }

    // ---------- Armado de respuestas ----------

    private LinkCargaTallesResponse aLinkResponse(CargaTallesPedido carga) {
        return new LinkCargaTallesResponse(carga.getPedido().getId(), carga.getToken(), carga.getEstado());
    }

    private ComboResponse aComboResponse(AlumnoProductoTalle combo) {
        return new ComboResponse(
            combo.getId(),
            combo.getProducto().getId(),
            combo.getProducto().getTipoPrenda() != null ? combo.getProducto().getTipoPrenda().getNombre() : null,
            combo.getAnchoCm(),
            combo.getLargoCm(),
            combo.getTalleAsignado() != null ? combo.getTalleAsignado().getTalle() : null,
            combo.isPersonalizado(),
            combo.getObservacionPersonalizado()
        );
    }

    private CargaTallesResponse construirRespuesta(CargaTallesPedido carga) {
        Pedido pedido = carga.getPedido();
        List<AlumnoPedido> alumnos = alumnoPedidoRepository.findByCargaTallesOrderByOrdenAsc(carga);
        List<AlumnoProductoTalle> todosLosCombos = alumnoProductoTalleRepository.findByAlumnoPedido_CargaTalles(carga);

        Map<Long, List<AlumnoProductoTalle>> combosPorAlumno = todosLosCombos.stream()
            .collect(Collectors.groupingBy(c -> c.getAlumnoPedido().getId()));

        List<AlumnoResponse> alumnosResponse = alumnos.stream()
            .map(alumno -> new AlumnoResponse(
                alumno.getId(),
                alumno.getNombreAlumno(),
                alumno.getOrden(),
                combosPorAlumno.getOrDefault(alumno.getId(), List.of()).stream()
                    .map(this::aComboResponse)
                    .toList()
            ))
            .toList();

        List<ProductoPedidoResumenResponse> productosResponse = pedido.getProductos().stream()
            .map(producto -> new ProductoPedidoResumenResponse(
                producto.getId(),
                producto.getTipoPrenda() != null ? producto.getTipoPrenda().getNombre() : "Sin tipo",
                producto.getCantidadTotal(),
                producto.getTipoPrenda() != null && producto.getTipoPrenda().getGrupoTalle() != null,
                alumnoProductoTalleRepository.countByProducto(producto),
                producto.getTipoPrenda() != null && producto.getTipoPrenda().getGrupoTalle() != null
                    ? producto.getTipoPrenda().getGrupoTalle().getNombre()
                    : null
            ))
            .toList();

        List<GrupoTallaResponse> tablasTalleResponse = pedido.getProductos().stream()
            .map(Producto::getTipoPrenda)
            .filter(tp -> tp != null && tp.getGrupoTalle() != null)
            .map(TipoPrenda::getGrupoTalle)
            .distinct()
            .map(grupo -> new GrupoTallaResponse(
                grupo.getNombre(),
                tablaTalleRepository.findByGrupoTalleOrderByOrdenAsc(grupo).stream()
                    .map(fila -> new FilaTablaTalleResponse(fila.getTalle(), fila.getAnchoCm(), fila.getLargoCm()))
                    .toList()
            ))
            .toList();

        return new CargaTallesResponse(
            pedido.getId(),
            pedido.getCodigoInterno(),
            pedido.getColegio().getNombre(),
            pedido.getColegio().getLocalidad(),
            pedido.getCurso(),
            pedido.getCantAlumnos(),
            carga.getEstado(),
            carga.getToken(),
            carga.getFechaCierre(),
            productosResponse,
            alumnosResponse,
            tablasTalleResponse
        );
    }
}
