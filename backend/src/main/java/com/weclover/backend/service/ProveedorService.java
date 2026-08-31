package com.weclover.backend.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.proveedor.ProveedorRequest;
import com.weclover.backend.dto.proveedor.ProveedorResponse;
import com.weclover.backend.entity.Proveedor;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.mapper.ProveedorMapper;
import com.weclover.backend.repository.ProveedorRepository;

import lombok.RequiredArgsConstructor;

/**
 * ABM del catálogo de proveedores. Es un catálogo comercialmente sensible (nombres y, a
 * futuro, precios cargados en ArticuloProveedor) — a diferencia de TipoTela/PaletaColores, acá
 * se restringe también la lectura a ROLE_ADMINISTRATIVO, no solo el alta/baja.
 */
@Service
@RequiredArgsConstructor
public class ProveedorService {

    private static final Set<String> ROLES_GESTION_PROVEEDORES = Set.of("ROLE_ADMINISTRATIVO");

    private final ProveedorRepository proveedorRepository;
    private final ProveedorMapper proveedorMapper;
    private final AutorizacionService autorizacionService;

    @Transactional(readOnly = true)
    public List<ProveedorResponse> listar(Boolean soloActivos, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_PROVEEDORES);

        List<Proveedor> proveedores = Boolean.TRUE.equals(soloActivos)
            ? proveedorRepository.findByActivoTrueOrderByNombreAsc()
            : proveedorRepository.findAllByOrderByNombreAsc();

        return proveedores.stream().map(proveedorMapper::toResponse).toList();
    }

    @Transactional
    public ProveedorResponse crear(ProveedorRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_PROVEEDORES);

        if (proveedorRepository.existsByCuit(request.cuit())) {
            throw new BusinessRuleException("Ya existe un proveedor cargado con el CUIT " + request.cuit());
        }

        Proveedor proveedor = Proveedor.builder()
            .cuit(request.cuit())
            .nombre(request.nombre())
            .activo(true)
            .build();

        return proveedorMapper.toResponse(proveedorRepository.save(proveedor));
    }

    @Transactional
    public ProveedorResponse actualizar(Long id, ProveedorRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_PROVEEDORES);

        Proveedor proveedor = obtenerProveedor(id);

        if (!proveedor.getCuit().equals(request.cuit()) && proveedorRepository.existsByCuit(request.cuit())) {
            throw new BusinessRuleException("Ya existe un proveedor cargado con el CUIT " + request.cuit());
        }

        proveedor.setCuit(request.cuit());
        proveedor.setNombre(request.nombre());

        return proveedorMapper.toResponse(proveedorRepository.save(proveedor));
    }

    @Transactional
    public ProveedorResponse cambiarActivo(Long id, boolean activo, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_PROVEEDORES);

        Proveedor proveedor = obtenerProveedor(id);
        proveedor.setActivo(activo);

        return proveedorMapper.toResponse(proveedorRepository.save(proveedor));
    }

    private Proveedor obtenerProveedor(Long id) {
        return proveedorRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el proveedor con id " + id));
    }
}
