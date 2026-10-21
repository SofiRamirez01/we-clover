package com.weclover.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.entity.Colegio;
import com.weclover.backend.entity.EstadoBandera;
import com.weclover.backend.entity.EstadoPedido;
import com.weclover.backend.entity.HistorialEstadoPedido;
import com.weclover.backend.entity.OrigenPedido;
import com.weclover.backend.entity.Pedido;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ResponsableCurso;
import com.weclover.backend.entity.Rol;
import com.weclover.backend.entity.TipoPrenda;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.repository.ColegioRepository;
import com.weclover.backend.repository.PedidoRepository;
import com.weclover.backend.repository.RolRepository;
import com.weclover.backend.repository.TipoPrendaRepository;
import com.weclover.backend.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Arma y persiste un Pedido a partir de una fila ya parseada del Excel de Kommo, en su propia
 * transacción (REQUIRES_NEW): si una fila tiene datos inválidos, solo se descarta esa fila —
 * las demás ya importadas antes o después no se ven afectadas. Ver PedidoImportService para el
 * parseo del archivo y el resumen de filas importadas/salteadas que arma con lo que esta clase
 * devuelve o tira.
 *
 * Reglas de mapeo acordadas con el negocio (no inventar datos que el Excel no trae):
 * - Email del representante: puede venir vacío (Usuario.email es nullable para este flujo,
 *   a diferencia del alta manual de un pedido que lo sigue exigiendo).
 * - Nº de ficha (codigoInterno): se regenera con el año de "Promo" + el número completo de la
 *   Ficha de Kommo (sin truncar). Al ser una transformación determinística, volver a importar
 *   la misma fila genera el mismo código — eso es lo que se usa para detectar filas ya
 *   importadas antes (existsByCodigoInterno) y no duplicar el pedido.
 * - Costo por prenda: si el pedido tiene un solo tipo de prenda (sin contar Bandera, que es
 *   regalo), su costo real es el "Precio Unitario" del Excel. Si tiene más de un tipo, no se
 *   inventa un costo por prenda — quedan en 0, el total real del Excel ("Presupuesto") se guarda
 *   aparte en Pedido.montoReferenciaImportado, y el "Precio Unitario" del Excel (que en Kommo es
 *   un precio por alumno/paquete, no por prenda — no se puede derivar como
 *   montoReferenciaImportado / unidades) se guarda aparte también en
 *   Pedido.precioUnitarioReferenciaImportado, para no perder ninguno de los dos datos.
 * - Bandera: cantidad fija 1 y costo 0 (se regala) cuando el Excel dice "Sí" — si en algún caso
 *   llevara precio hay que cargarlo a mano desde el pedido ya importado.
 * - cantAlumnos: no hay columna directa — se aproxima con la cantidad máxima entre las prendas
 *   del pedido.
 * - curso: no hay columna directa — queda un placeholder ("Sin curso") a completar a mano.
 * - Origen: KOMMO si la fila trae kommoLeadId (API de Kommo), EXCEL si no; en los dos casos el
 *   pedido queda sin revisar (Pedido.revisadoImportacion = false) hasta que se guarda desde el
 *   formulario de edición.
 * - Fuera de los datos de la fila, el alta hace lo mismo que la manual (PedidoService.crearPedido):
 *   tela por defecto por tipo de prenda, link de carga de talles y etapas de producción.
 */
@Service
@RequiredArgsConstructor
class PedidoImportRowService {

    private static final String ROL_CLIENTE = "ROLE_CLIENTE";
    private static final DateTimeFormatter FORMATO_FECHA_KOMMO = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Pattern NO_ALFANUMERICO = Pattern.compile("[^A-Z0-9]+");

    private static final Map<String, String> COLUMNA_A_TIPO_PRENDA = new LinkedHashMap<>();
    static {
        COLUMNA_A_TIPO_PRENDA.put("buzos", "Buzo");
        COLUMNA_A_TIPO_PRENDA.put("camperas", "Campera");
        COLUMNA_A_TIPO_PRENDA.put("remeras", "Remera");
        COLUMNA_A_TIPO_PRENDA.put("chombas", "Chomba");
    }

    private final PedidoRepository pedidoRepository;
    private final ColegioRepository colegioRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final TipoPrendaRepository tipoPrendaRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProductoEtapaProduccionService productoEtapaProduccionService;
    private final CargaTallesService cargaTallesService;
    private final TipoTelaPorDefecto tipoTelaPorDefecto;

    /**
     * Datos de una fila ya validados por {@link #validarFila}, listos para armar el Pedido sin
     * ninguna otra verificación: vendedor, representanteExistente, rolCliente y los tipos de
     * prenda son entidades ya leídas (no se modifican); codigoInterno ya se verificó que no exista.
     *
     * @param cantidadPorTipo        prendas (sin Bandera) con cantidad > 0, en el orden del Excel
     * @param bandera                tipo de prenda Bandera si el pedido la lleva, si no null
     * @param representanteExistente usuario con el email del contacto, si ya existe (se reutiliza)
     * @param rolCliente             rol con el que se crea el representante si no existe; null si
     *                               no hace falta crearlo
     */
    record FilaValidada(
        String colegio,
        String localidad,
        String contacto,
        EstadoPedido estado,
        Usuario vendedor,
        LocalDate fechaVenta,
        LocalDate fechaEntrega,
        Map<TipoPrenda, Integer> cantidadPorTipo,
        TipoPrenda bandera,
        Float precioUnitario,
        Float presupuesto,
        boolean costoPorPrendaConocido,
        int cantAlumnos,
        String codigoInterno,
        Usuario representanteExistente,
        Rol rolCliente
    ) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Pedido importarFila(FilaExcelPedido fila) {
        FilaValidada validada = validarFila(fila);
        EstadoPedido estado = validada.estado();
        Usuario vendedor = validada.vendedor();
        boolean costoPorPrendaConocido = validada.costoPorPrendaConocido();
        Float precioUnitario = validada.precioUnitario();
        Float presupuesto = validada.presupuesto();

        Colegio colegio = colegioRepository.save(Colegio.builder()
            .nombre(validada.colegio())
            .localidad(validada.localidad())
            .provincia(blankANull(fila.provincia()))
            .nivel(blankANull(fila.nivel()))
            .build());

        Usuario representante = validada.representanteExistente() != null
            ? validada.representanteExistente()
            : crearRepresentante(validada.contacto(), blankANull(fila.telefonoContacto()),
                blankANull(fila.emailContacto()), validada.rolCliente());
        boolean desdeKommo = fila.kommoLeadId() != null;

        Pedido pedido = Pedido.builder()
            .colegio(colegio)
            .representanteCurso(representante)
            .creadoPor(vendedor)
            .estadoActual(estado)
            .codigoInterno(validada.codigoInterno())
            .curso("Sin curso")
            .cantAlumnos(validada.cantAlumnos())
            .observaciones(armarObservaciones(fila))
            .fechaVenta(validada.fechaVenta())
            .fechaEstimadaEntrega(validada.fechaEntrega())
            .responsableCurso(resolverResponsableCurso(fila.responsableCurso()))
            .contratoFirmado(esSi(fila.contrato()))
            .cantidadCuotas(parsearEnteroONulo(fila.cuotas()))
            .montoReferenciaImportado(costoPorPrendaConocido ? null : presupuesto)
            .precioUnitarioReferenciaImportado(costoPorPrendaConocido ? null : precioUnitario)
            .origen(desdeKommo ? OrigenPedido.KOMMO : OrigenPedido.EXCEL)
            .kommoLeadId(fila.kommoLeadId())
            .revisadoImportacion(false)
            .build();

        pedido.getHistorial().add(HistorialEstadoPedido.builder()
            .pedido(pedido)
            .estado(estado)
            .fechaCambio(LocalDateTime.now())
            .modificadoPor(vendedor)
            .observaciones(desdeKommo
                ? "Alta por importación desde Kommo, lead #" + fila.kommoLeadId()
                : "Alta por importación de Excel (Kommo), fila " + fila.filaExcel())
            .build());

        for (Map.Entry<TipoPrenda, Integer> entry : validada.cantidadPorTipo().entrySet()) {
            pedido.getProductos().add(Producto.builder()
                .pedido(pedido)
                .tipoPrenda(entry.getKey())
                .tipoTela(tipoTelaPorDefecto.resolver(entry.getKey()))
                .cantidadTotal(entry.getValue())
                .costo(costoPorPrendaConocido ? precioUnitario : 0f)
                .build());
        }
        if (validada.bandera() != null) {
            pedido.getProductos().add(Producto.builder()
                .pedido(pedido)
                .tipoPrenda(validada.bandera())
                .tipoTela(tipoTelaPorDefecto.resolver(validada.bandera()))
                .cantidadTotal(1)
                .costo(0f)
                .estadoBandera(EstadoBandera.PENDIENTE)
                .build());
        }

        Pedido guardado = pedidoRepository.save(pedido);
        cargaTallesService.generarLinkParaPedidoNuevo(guardado);
        guardado.getProductos().stream()
            .filter(producto -> !EtapaProduccionAplicabilidad.esBandera(producto))
            .forEach(productoEtapaProduccionService::sincronizarEtapas);
        return guardado;
    }

    /**
     * Todas las reglas de una fila, sin escribir nada: campos requeridos, estado, vendedor,
     * fechas, cantidades, lead de Kommo ya importado, Nº de ficha (formato, coherencia con Promo
     * y duplicado), rol del representante y tipos de prenda del catálogo. Es la única fuente de
     * motivos de salteo: importarFila no valida nada más, así que la simulación de la
     * importación desde Kommo reporta exactamente los mismos motivos que la importación real.
     */
    @Transactional(readOnly = true)
    public FilaValidada validarFila(FilaExcelPedido fila) {
        String colegioNombre = requerido(fila.colegio(), "el Colegio");
        String localidad = requerido(fila.localidad(), "la Localidad");
        String contacto = requerido(fila.contactoPrincipal(), "el Contacto principal");

        EstadoPedido estado = resolverEstado(fila.etiquetaEstado());
        Usuario vendedor = resolverVendedor(fila);
        LocalDate fechaVenta = parsearFecha(fila.fechaVenta(), "Fecha Venta");
        LocalDate fechaEntrega = parsearFecha(fila.fechaEntregaPactada(), "Fecha Entrega Pactada");
        if (fechaEntrega.isBefore(fechaVenta)) {
            throw new BusinessRuleException(
                "Fecha Entrega Pactada (" + fechaEntrega + ") es anterior a Fecha Venta (" + fechaVenta + ")");
        }

        Map<String, Integer> cantidadPorTipo = new LinkedHashMap<>();
        // Map.of no acepta null, y los campos que el lead de Kommo no trae llegan en null.
        for (Map.Entry<String, String> col : Map.of(
            "buzos", nuloAVacio(fila.buzos()), "camperas", nuloAVacio(fila.camperas()),
            "remeras", nuloAVacio(fila.remeras()), "chombas", nuloAVacio(fila.chombas())).entrySet()) {
            Integer cantidadParseada = parsearEnteroONulo(col.getValue());
            int cantidad = cantidadParseada != null ? cantidadParseada : 0;
            if (cantidad > 0) {
                cantidadPorTipo.put(COLUMNA_A_TIPO_PRENDA.get(col.getKey()), cantidad);
            }
        }
        boolean llevaBandera = esSi(fila.bandera());
        if (cantidadPorTipo.isEmpty() && !llevaBandera) {
            throw new BusinessRuleException("No tiene cantidad cargada en ninguna prenda (Buzos/Camperas/Remeras/Chombas/Bandera)");
        }

        Float precioUnitario = parsearDecimalONulo(fila.precioUnitario());
        Float presupuesto = parsearDecimalONulo(fila.presupuesto());
        boolean costoPorPrendaConocido = cantidadPorTipo.size() == 1 && precioUnitario != null;

        int cantAlumnos = cantidadPorTipo.values().stream().mapToInt(Integer::intValue).max()
            .orElse(llevaBandera ? 1 : 1);

        if (fila.kommoLeadId() != null) {
            pedidoRepository.findByKommoLeadId(fila.kommoLeadId()).ifPresent(existente -> {
                throw new BusinessRuleException(
                    "El lead #" + fila.kommoLeadId() + " ya se importó como pedido " + existente.getCodigoInterno());
            });
        }
        String codigoInterno = generarCodigoInterno(fila.promo(), fila.fichaKommo());

        String email = blankANull(fila.emailContacto());
        Usuario representanteExistente = email == null ? null : usuarioRepository.findByEmail(email).orElse(null);
        Rol rolCliente = representanteExistente != null ? null : rolRepository.findByNombre(ROL_CLIENTE)
            .orElseThrow(() -> new ResourceNotFoundException(
                "No existe el rol " + ROL_CLIENTE + " necesario para dar de alta al representante"));

        Map<TipoPrenda, Integer> prendas = new LinkedHashMap<>();
        cantidadPorTipo.forEach((nombre, cantidad) -> prendas.put(tipoPrendaDelCatalogo(nombre), cantidad));
        TipoPrenda bandera = llevaBandera ? tipoPrendaDelCatalogo("Bandera") : null;

        return new FilaValidada(colegioNombre, localidad, contacto, estado, vendedor, fechaVenta, fechaEntrega,
            prendas, bandera, precioUnitario, presupuesto, costoPorPrendaConocido, cantAlumnos,
            codigoInterno, representanteExistente, rolCliente);
    }

    private TipoPrenda tipoPrendaDelCatalogo(String nombre) {
        return tipoPrendaRepository.findByNombre(nombre)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el tipo de prenda " + nombre + " en el catálogo"));
    }

    private static String nuloAVacio(String valor) {
        return valor == null ? "" : valor;
    }

    private static String requerido(String valor, String nombreCampo) {
        if (valor == null || valor.isBlank()) {
            throw new BusinessRuleException("Falta " + nombreCampo);
        }
        return valor.trim();
    }

    private String blankANull(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    static boolean esSi(String valor) {
        if (valor == null) return false;
        String v = valor.trim().toLowerCase();
        return v.equals("si") || v.equals("sí");
    }

    private EstadoPedido resolverEstado(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            throw new BusinessRuleException("Falta la Etiqueta del lead (se usa como estado del pedido)");
        }
        for (String token : etiqueta.split(",")) {
            String normalizado = normalizar(token);
            for (EstadoPedido estado : EstadoPedido.values()) {
                if (estado.name().equals(normalizado)) {
                    return estado;
                }
            }
        }
        throw new BusinessRuleException("La etiqueta '" + etiqueta + "' no coincide con ningún EstadoPedido");
    }

    private String normalizar(String texto) {
        String sinAcentos = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        return NO_ALFANUMERICO.matcher(sinAcentos.toUpperCase()).replaceAll("_").replaceAll("^_|_$", "");
    }

    /**
     * Desde la API de Kommo (viene vendedorKommoEnumId): el usuario vinculado a esa opción del
     * campo Vendedor (Usuario.kommoVendedorEnumId). Desde el Excel: búsqueda por nombre, porque
     * la columna "Responsable" trae un apodo corto (ej. "Sofi"), no un id.
     */
    private Usuario resolverVendedor(FilaExcelPedido fila) {
        if (fila.vendedorKommoEnumId() != null) {
            String nombreKommo = fila.responsableVendedor() != null ? fila.responsableVendedor() : "#" + fila.vendedorKommoEnumId();
            Usuario vinculado = usuarioRepository.findByKommoVendedorEnumId(fila.vendedorKommoEnumId())
                .orElseThrow(() -> new BusinessRuleException(
                    "El vendedor de Kommo '" + nombreKommo + "' no está vinculado a ningún usuario"));
            if (!vinculado.isHabilitado()) {
                throw new BusinessRuleException("El vendedor de Kommo '" + nombreKommo + "' está vinculado a "
                    + vinculado.getNombre() + ", que está deshabilitado");
            }
            return vinculado;
        }
        String nombre = requerido(fila.responsableVendedor(), "el Responsable (vendedor)");
        return usuarioRepository.findFirstByNombreContainingIgnoreCaseAndHabilitadoTrue(nombre)
            .orElseThrow(() -> new ResourceNotFoundException(
                "No se encontró un usuario habilitado cuyo nombre contenga '" + nombre + "' para asignar como vendedor"));
    }

    private ResponsableCurso resolverResponsableCurso(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String v = valor.trim().toLowerCase();
        if (v.equals("alumno")) return ResponsableCurso.ALUMNO;
        if (v.equals("adulto")) return ResponsableCurso.ADULTO;
        return null;
    }

    private LocalDate parsearFecha(String valor, String nombreCampo) {
        String v = requerido(valor, nombreCampo);
        try {
            return LocalDate.parse(v, FORMATO_FECHA_KOMMO);
        } catch (Exception e) {
            throw new BusinessRuleException("No se pudo interpretar " + nombreCampo + " ('" + v + "'), se espera DD.MM.AAAA");
        }
    }

    static Integer parsearEnteroONulo(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Float parsearDecimalONulo(String valor) {
        if (valor == null || valor.isBlank()) return null;
        try {
            return Float.parseFloat(valor.trim().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * AAAA = año de Promo, NN = número completo de la Ficha de Kommo, nunca truncado (el
     * correlativo de un pedido creado a mano tampoco tiene un límite de dígitos — ver el
     * @Pattern de PedidoCreateRequest/PedidoUpdateRequest, "^\d{4}-\d{2,}$": NN crece más allá
     * de 2 dígitos una vez superados los 99 pedidos del año, no se trunca nunca). Se completa
     * con un cero a la izquierda solo si Kommo da un número de un solo dígito, para no bajar del
     * mínimo de 2 dígitos que exige ese mismo patrón. Al ser una transformación determinística
     * de la Ficha de Kommo, volver a importar la misma fila genera el mismo código — eso es lo
     * que permite detectar duplicados acá abajo con existsByCodigoInterno, en vez de guardar el
     * número de Kommo aparte solo para compararlo.
     */
    private String generarCodigoInterno(String promo, String fichaKommo) {
        String codigo = armarCodigoInterno(promo, fichaKommo);
        if (pedidoRepository.existsByCodigoInterno(codigo)) {
            throw new BusinessRuleException(
                "Ya existe un pedido con el código " + codigo + " — probablemente esta fila (Ficha Kommo '"
                    + fichaKommo.trim() + "') ya se importó antes");
        }
        return codigo;
    }

    /** AAAA-NN a partir de Promo y Ficha (ver generarCodigoInterno), sin chequear duplicados. */
    static String armarCodigoInterno(String promo, String fichaKommo) {
        String promoTrim = requerido(promo, "Promo (se usa para el año del Nº de ficha)");
        int anio;
        try {
            anio = Integer.parseInt(promoTrim);
        } catch (NumberFormatException e) {
            throw new BusinessRuleException("Promo ('" + promoTrim + "') no es un año válido");
        }
        String fichaTrim = requerido(fichaKommo, "Ficha (se usa para generar el Nº de ficha y detectar duplicados)");
        String numeroKommo = fichaTrim.contains("-") ? fichaTrim.substring(0, fichaTrim.indexOf('-')) : fichaTrim;
        numeroKommo = numeroKommo.trim();
        if (numeroKommo.isEmpty() || !numeroKommo.chars().allMatch(Character::isDigit)) {
            throw new BusinessRuleException("No se pudo interpretar el número de la Ficha ('" + fichaTrim + "') para generar el Nº de ficha");
        }
        if (fichaTrim.contains("-")) {
            validarSufijoFicha(fichaTrim.substring(fichaTrim.indexOf('-') + 1).trim(), fichaTrim, anio);
        }
        if (numeroKommo.length() < 2) {
            numeroKommo = "0" + numeroKommo;
        }
        return anio + "-" + numeroKommo;
    }

    /**
     * El sufijo de la Ficha (lo que va después del guion, ej. "27" en "241-27") es el año de la
     * promoción: tiene que coincidir con Promo, con dos dígitos (últimos dos del año) o cuatro
     * (año completo). Cualquier otra cosa no se interpreta.
     */
    private static void validarSufijoFicha(String sufijo, String ficha, int anioPromo) {
        boolean soloDigitos = !sufijo.isEmpty() && sufijo.chars().allMatch(Character::isDigit);
        if (!soloDigitos || (sufijo.length() != 2 && sufijo.length() != 4)) {
            throw new BusinessRuleException("Sufijo de Ficha no interpretable: Ficha '" + ficha
                + "' (se espera el año de la promoción después del guion, ej. '241-27')");
        }
        int anioSufijo = Integer.parseInt(sufijo);
        boolean coincide = sufijo.length() == 2 ? anioSufijo == anioPromo % 100 : anioSufijo == anioPromo;
        if (!coincide) {
            throw new BusinessRuleException("La Ficha '" + ficha + "' no coincide con la Promo " + anioPromo
                + " (el sufijo '" + sufijo + "' tendría que ser el año de la promoción)");
        }
    }

    private String armarObservaciones(FilaExcelPedido fila) {
        StringBuilder sb = new StringBuilder();
        boolean pique = esSi(fila.pique());
        boolean tejido = esSi(fila.tejido());
        if (pique && tejido) {
            sb.append("Lleva Pique y Tejido");
        } else if (pique) {
            sb.append("Lleva Pique");
        } else if (tejido) {
            sb.append("Lleva Tejido");
        }
        for (String nota : fila.notas()) {
            if (nota != null && !nota.isBlank()) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(nota.trim());
            }
        }
        if (sb.length() == 0) return null;
        return sb.length() > 500 ? sb.substring(0, 500) : sb.toString();
    }

    /**
     * El representante de curso todavía no tiene alta ni login propios (mismo criterio que
     * PedidoService.obtenerOCrearRepresentante). A diferencia de esa versión, acá el email puede
     * no venir: si falta, no hay con qué buscar uno existente y se crea siempre uno nuevo (evita
     * que dos representantes sin email distintos se confundan buscando por email null). Si el
     * email ya existe, validarFila ya trajo ese usuario y no se llega acá.
     */
    private Usuario crearRepresentante(String nombre, String telefono, String email, Rol rolCliente) {
        return usuarioRepository.save(Usuario.builder()
            .rol(rolCliente)
            .nombre(nombre)
            .email(email)
            .telefono(telefono)
            .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
            .habilitado(true)
            .build());
    }
}
