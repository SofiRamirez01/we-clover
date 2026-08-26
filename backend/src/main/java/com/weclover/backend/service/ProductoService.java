package com.weclover.backend.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.weclover.backend.dto.producto.ActualizarColorCierreRequest;
import com.weclover.backend.dto.producto.ActualizarTipoTelaRequest;
import com.weclover.backend.dto.producto.CambioEstadoProductoRequest;
import com.weclover.backend.dto.producto.ProductoColorItemRequest;
import com.weclover.backend.dto.producto.ProductoColoresRequest;
import com.weclover.backend.dto.producto.ProductoResponse;
import com.weclover.backend.entity.MetodoDeteccionColor;
import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.PatronCorteColor;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoColor;
import com.weclover.backend.entity.TipoTela;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.mapper.ProductoMapper;
import com.weclover.backend.repository.PaletaColoresRepository;
import com.weclover.backend.repository.ProductoRepository;

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

    /** Roles habilitados para cambiar el estado de producción de una prenda. */
    private static final Set<String> ROLES_CAMBIO_ESTADO = Set.of(
        "ROLE_ADMINISTRATIVO", "ROLE_PLANTA"
    );

    /** Único tipo de prenda que tiene cierre (ver actualizarColorCierre y el default en asignarColores). */
    private static final String TIPO_PRENDA_CAMPERA = "Campera";

    private final ProductoRepository productoRepository;
    private final PaletaColoresRepository paletaColoresRepository;
    private final ProductoMapper productoMapper;
    private final AutorizacionService autorizacionService;
    private final AlmacenamientoImagenService almacenamientoImagenService;

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

        return productoMapper.toResponse(productoRepository.save(producto));
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

        return productoMapper.toResponse(productoRepository.save(producto));
    }

    @Transactional
    public ProductoResponse cambiarEstado(Long idProducto, CambioEstadoProductoRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_CAMBIO_ESTADO);

        Producto producto = productoRepository.findById(idProducto)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el producto con id " + idProducto));

        producto.setEstadoActual(request.estado());

        return productoMapper.toResponse(productoRepository.save(producto));
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

        // Default del color de cierre: mismo nombre que el "Color 1" recién elegido, pero
        // buscado dentro de los colores catalogados como CIERRE (no necesariamente la misma
        // fila, porque esa es la de la tela). Si no existe un color de cierre con ese nombre
        // exacto, queda sin definir y hay que elegirlo a mano (ver actualizarColorCierre). No
        // pisa una elección manual ya guardada.
        if (esCampera(producto) && producto.getColorCierre() == null && colorPosicionUno != null) {
            paletaColoresRepository.findByNombreIgnoreCaseAndTipoTela(colorPosicionUno.getNombre(), TipoTela.CIERRE)
                .ifPresent(producto::setColorCierre);
        }

        return productoMapper.toResponse(productoRepository.save(producto));
    }

    /**
     * Tela de esta prenda puntual. Nunca se acepta CIERRE acá (esa categoría del enum es
     * solo para el catálogo de colores de cierre, no para la tela de una prenda).
     */
    @Transactional
    public ProductoResponse actualizarTipoTela(Long idProducto, ActualizarTipoTelaRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_CARGA_DISENIO);

        if (request.tipoTela() == TipoTela.CIERRE) {
            throw new BusinessRuleException("CIERRE no es un tipo de tela válido para una prenda");
        }

        Producto producto = productoRepository.findById(idProducto)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el producto con id " + idProducto));

        producto.setTipoTela(request.tipoTela());

        return productoMapper.toResponse(productoRepository.save(producto));
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

        if (color.getTipoTela() != TipoTela.CIERRE) {
            throw new BusinessRuleException("El color elegido no es un color de cierre");
        }

        producto.setColorCierre(color);

        return productoMapper.toResponse(productoRepository.save(producto));
    }

    private boolean esCampera(Producto producto) {
        return producto.getTipoPrenda() != null
            && TIPO_PRENDA_CAMPERA.equalsIgnoreCase(producto.getTipoPrenda().getNombre());
    }
}
