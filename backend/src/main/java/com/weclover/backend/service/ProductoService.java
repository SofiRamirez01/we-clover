package com.weclover.backend.service;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.weclover.backend.dto.producto.CambioEstadoProductoRequest;
import com.weclover.backend.dto.producto.ProductoResponse;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.mapper.ProductoMapper;
import com.weclover.backend.repository.ProductoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductoService {

    /** Roles habilitados para cargar/reemplazar la imagen de diseño de una prenda (ficha técnica). */
    private static final Set<String> ROLES_CARGA_DISENIO = Set.of(
        "ROLE_ADMINISTRATIVO", "ROLE_VENDEDOR", "ROLE_DISENADOR"
    );

    /** Roles habilitados para cambiar el estado de producción de una prenda. */
    private static final Set<String> ROLES_CAMBIO_ESTADO = Set.of(
        "ROLE_ADMINISTRATIVO", "ROLE_PLANTA"
    );

    private final ProductoRepository productoRepository;
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

    @Transactional
    public ProductoResponse cambiarEstado(Long idProducto, CambioEstadoProductoRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_CAMBIO_ESTADO);

        Producto producto = productoRepository.findById(idProducto)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el producto con id " + idProducto));

        producto.setEstadoActual(request.estado());

        return productoMapper.toResponse(productoRepository.save(producto));
    }
}
