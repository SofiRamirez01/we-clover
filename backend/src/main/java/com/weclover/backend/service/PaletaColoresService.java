package com.weclover.backend.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.paletacolores.PaletaColorCreateRequest;
import com.weclover.backend.dto.paletacolores.PaletaColorResponse;
import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.TipoTela;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.mapper.PaletaColorMapper;
import com.weclover.backend.repository.PaletaColoresRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaletaColoresService {

    /** Roles habilitados para dar de alta un color nuevo en la paleta (mismos que cargan la imagen de diseño). */
    private static final Set<String> ROLES_ALTA_COLOR = Set.of(
        "ROLE_ADMINISTRATIVO", "ROLE_VENDEDOR", "ROLE_DISENADOR"
    );

    private final PaletaColoresRepository paletaColoresRepository;
    private final PaletaColorMapper paletaColorMapper;
    private final AutorizacionService autorizacionService;

    /**
     * Lectura sin restricción de rol: es un catálogo de solo lectura y baja sensibilidad,
     * igual criterio que GET /api/tipos-prenda. `tipoTela` es opcional: si se indica, solo
     * devuelve los colores de esa tela (o de "cierre").
     */
    @Transactional(readOnly = true)
    public List<PaletaColorResponse> listarActivos(TipoTela tipoTela) {
        List<PaletaColores> colores = tipoTela != null
            ? paletaColoresRepository.findByActivoTrueAndTipoTelaOrderByNombreAsc(tipoTela)
            : paletaColoresRepository.findByActivoTrueOrderByNombreAsc();

        return colores.stream()
            .map(paletaColorMapper::toResponse)
            .toList();
    }

    @Transactional
    public PaletaColorResponse crearColor(PaletaColorCreateRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_ALTA_COLOR);

        if (paletaColoresRepository.existsByNombreIgnoreCaseAndTipoTela(request.nombre(), request.tipoTela())) {
            throw new BusinessRuleException(
                "Ya existe un color de " + request.tipoTela() + " en la paleta con el nombre " + request.nombre());
        }

        PaletaColores paletaColor = PaletaColores.builder()
            .nombre(request.nombre())
            .hex(request.hex().toUpperCase())
            .tipoTela(request.tipoTela())
            .activo(true)
            .build();

        return paletaColorMapper.toResponse(paletaColoresRepository.save(paletaColor));
    }
}
