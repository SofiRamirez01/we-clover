package com.weclover.backend.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.patroncorte.PatronCorteCreateRequest;
import com.weclover.backend.dto.patroncorte.PatronCorteResponse;
import com.weclover.backend.entity.PatronCorte;
import com.weclover.backend.entity.PatronCorteColor;
import com.weclover.backend.entity.TipoPrenda;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.mapper.PatronCorteMapper;
import com.weclover.backend.repository.PatronCorteRepository;
import com.weclover.backend.repository.TipoPrendaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PatronCorteService {

    private final PatronCorteRepository patronCorteRepository;
    private final TipoPrendaRepository tipoPrendaRepository;
    private final PatronCorteMapper patronCorteMapper;
    private final AutorizacionService autorizacionService;
    private final AlmacenamientoImagenService almacenamientoImagenService;

    @Value("${app.uploads.patrones-corte-dir}")
    private String directorioUploads;

    @Value("${app.uploads.patrones-corte-url-base}")
    private String urlBase;

    @Transactional
    public PatronCorteResponse crearPatronCorte(PatronCorteCreateRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);

        List<Integer> gramosPorColor = request.gramosPorColor();

        for (Integer gramos : gramosPorColor) {
            if (gramos == null || gramos <= 0) {
                throw new BusinessRuleException("Los gramos de cada color deben ser mayores a cero");
            }
        }

        if (patronCorteRepository.existsByNumeroInterno(request.numeroInterno())) {
            throw new BusinessRuleException(
                "Ya existe un patrón de corte con el número interno " + request.numeroInterno());
        }

        TipoPrenda tipoPrenda = tipoPrendaRepository.findById(request.idTipoPrenda())
            .orElseThrow(() -> new ResourceNotFoundException(
                "No existe el tipo de prenda con id " + request.idTipoPrenda()));

        String imagenUrl = almacenamientoImagenService.guardar(request.imagen(), directorioUploads, urlBase);

        PatronCorte patronCorte = PatronCorte.builder()
            .numeroInterno(request.numeroInterno())
            .nombre(request.nombre())
            .tipoPrenda(tipoPrenda)
            .imagenUrl(imagenUrl)
            .cantidadColores(gramosPorColor.size())
            .activo(true)
            .build();

        for (int i = 0; i < gramosPorColor.size(); i++) {
            patronCorte.getColores().add(PatronCorteColor.builder()
                .patronCorte(patronCorte)
                .orden(i + 1)
                .gramos(gramosPorColor.get(i))
                .build());
        }

        PatronCorte guardado = patronCorteRepository.save(patronCorte);
        return patronCorteMapper.toResponse(guardado);
    }

    @Transactional(readOnly = true)
    public List<PatronCorteResponse> listarActivos(Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);

        return patronCorteRepository.findByActivoTrueOrderByNombreAsc().stream()
            .map(patronCorteMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public PatronCorteResponse obtenerPatronCorte(Long id, Long idUsuarioActor) {
        autorizacionService.verificarRolAdministrativo(idUsuarioActor);

        PatronCorte patronCorte = patronCorteRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el patrón de corte con id " + id));
        return patronCorteMapper.toResponse(patronCorte);
    }
}
