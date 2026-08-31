package com.weclover.backend.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.proveedor.ArticuloProveedorRequest;
import com.weclover.backend.dto.proveedor.ArticuloProveedorResponse;
import com.weclover.backend.dto.proveedor.ArticuloProveedorUpdateRequest;
import com.weclover.backend.entity.ArticuloProveedor;
import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.Proveedor;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.mapper.ArticuloProveedorMapper;
import com.weclover.backend.repository.ArticuloProveedorRepository;
import com.weclover.backend.repository.PaletaColoresRepository;
import com.weclover.backend.repository.ProveedorRepository;

import lombok.RequiredArgsConstructor;

/**
 * ABM de la relación Proveedor-Color (qué proveedores pueden proveer cada color de la carta,
 * en qué unidad y a qué precio estimado). Mismo criterio de roles que ProveedorService: solo
 * ROLE_ADMINISTRATIVO, tanto para lectura como para escritura, por ser datos de precios.
 */
@Service
@RequiredArgsConstructor
public class ArticuloProveedorService {

    private static final Set<String> ROLES_GESTION_PROVEEDORES = Set.of("ROLE_ADMINISTRATIVO");

    private final ArticuloProveedorRepository articuloProveedorRepository;
    private final ProveedorRepository proveedorRepository;
    private final PaletaColoresRepository paletaColoresRepository;
    private final ArticuloProveedorMapper articuloProveedorMapper;
    private final AutorizacionService autorizacionService;

    @Transactional
    public ArticuloProveedorResponse agregarArticulo(Long idProveedor, ArticuloProveedorRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_PROVEEDORES);

        Proveedor proveedor = proveedorRepository.findById(idProveedor)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el proveedor con id " + idProveedor));
        if (!proveedor.isActivo()) {
            throw new BusinessRuleException("El proveedor está dado de baja, no se le pueden cargar artículos nuevos");
        }

        PaletaColores paletaColor = paletaColoresRepository.findById(request.idPaletaColor())
            .orElseThrow(() -> new ResourceNotFoundException("No existe el color con id " + request.idPaletaColor()));

        if (articuloProveedorRepository.existsByProveedorAndPaletaColor(proveedor, paletaColor)) {
            throw new BusinessRuleException(
                "Ya existe un artículo cargado para " + proveedor.getNombre() + " en el color " + paletaColor.getNombre()
                    + " — edítelo en vez de crear uno nuevo");
        }

        if (request.preferido()) {
            limpiarPreferidoAnterior(paletaColor, null);
        }

        ArticuloProveedor articulo = ArticuloProveedor.builder()
            .proveedor(proveedor)
            .paletaColor(paletaColor)
            .unidadMedida(request.unidadMedida())
            .precioEstimado(request.precioEstimado())
            .preferido(request.preferido())
            .activo(true)
            .build();

        return articuloProveedorMapper.toResponse(articuloProveedorRepository.save(articulo));
    }

    @Transactional
    public ArticuloProveedorResponse actualizarArticulo(Long id, ArticuloProveedorUpdateRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_PROVEEDORES);

        ArticuloProveedor articulo = articuloProveedorRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el artículo de proveedor con id " + id));

        if (request.preferido() && !articulo.isPreferido()) {
            limpiarPreferidoAnterior(articulo.getPaletaColor(), articulo.getId());
        }

        articulo.setUnidadMedida(request.unidadMedida());
        articulo.setPrecioEstimado(request.precioEstimado());
        articulo.setPreferido(request.preferido());
        articulo.setActivo(request.activo());

        return articuloProveedorMapper.toResponse(articuloProveedorRepository.save(articulo));
    }

    /** Hace cumplir "a lo sumo un preferido por color": desmarca la fila que hoy lo tenga (si
     *  hay alguna distinta de idArticuloExcluir) antes de que otra pase a ser la preferida. */
    private void limpiarPreferidoAnterior(PaletaColores paletaColor, Long idArticuloExcluir) {
        articuloProveedorRepository.findByPaletaColorAndPreferidoTrue(paletaColor)
            .filter(actual -> !actual.getId().equals(idArticuloExcluir))
            .ifPresent(actual -> {
                actual.setPreferido(false);
                articuloProveedorRepository.save(actual);
            });
    }

    @Transactional(readOnly = true)
    public List<ArticuloProveedorResponse> listarPorProveedor(Long idProveedor, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_PROVEEDORES);

        Proveedor proveedor = proveedorRepository.findById(idProveedor)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el proveedor con id " + idProveedor));

        return articuloProveedorRepository.findByProveedor(proveedor).stream()
            .map(articuloProveedorMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ArticuloProveedorResponse> listarPorPaletaColor(Long idPaletaColor, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_PROVEEDORES);

        PaletaColores paletaColor = paletaColoresRepository.findById(idPaletaColor)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el color con id " + idPaletaColor));

        return articuloProveedorRepository.findByPaletaColor(paletaColor).stream()
            .map(articuloProveedorMapper::toResponse)
            .toList();
    }
}
