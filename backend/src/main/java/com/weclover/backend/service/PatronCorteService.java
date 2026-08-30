package com.weclover.backend.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * Roles habilitados para LEER patrones de corte (listado y detalle): además de
     * ROLE_ADMINISTRATIVO, ahora también Vendedor (elige el patrón al cargar un pedido) y
     * Diseñador (lee las posiciones de color para el modal de gotero). El alta sigue
     * reservada a ROLE_ADMINISTRATIVO (ver crearPatronCorte).
     */
    private static final Set<String> ROLES_LECTURA = Set.of(
        "ROLE_ADMINISTRATIVO", "ROLE_VENDEDOR", "ROLE_DISENADOR"
    );

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

        List<TipoPrenda> tiposPrenda = request.idsTipoPrenda().stream()
            .distinct()
            .map(id -> tipoPrendaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No existe el tipo de prenda con id " + id)))
            .toList();

        validarNumeroInternoDisponible(request.numeroInterno(), tiposPrenda);

        String imagenUrl = almacenamientoImagenService.guardar(request.imagen(), directorioUploads, urlBase);

        PatronCorte patronCorte = PatronCorte.builder()
            .numeroInterno(request.numeroInterno())
            .nombre(request.nombre())
            .tiposPrenda(tiposPrenda)
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

    /**
     * numeroInterno no es único a nivel global: dos molderías que no comparten ningún tipo de
     * prenda pueden repetir número (ej. moldería #1 de Buzo/Campera y moldería #1 de
     * Chomba/Remera son numeraciones independientes, una por "familia" de tipo de prenda). Solo
     * es inválido si ya existe una moldería con ese número para alguno de los tipos de prenda
     * que se está por asignar.
     */
    private void validarNumeroInternoDisponible(Integer numeroInterno, List<TipoPrenda> tiposPrenda) {
        List<Long> idsTipoPrenda = tiposPrenda.stream().map(TipoPrenda::getId).toList();
        List<PatronCorte> conflictos = patronCorteRepository
            .findDistinctByNumeroInternoAndTiposPrenda_IdIn(numeroInterno, idsTipoPrenda);

        if (!conflictos.isEmpty()) {
            String tiposEnConflicto = conflictos.stream()
                .flatMap(patron -> patron.getTiposPrenda().stream())
                .filter(tipo -> idsTipoPrenda.contains(tipo.getId()))
                .map(TipoPrenda::getNombre)
                .distinct()
                .collect(Collectors.joining(", "));
            throw new BusinessRuleException(
                "Ya existe una moldería con el número " + numeroInterno + " para: " + tiposEnConflicto);
        }
    }

    @Transactional(readOnly = true)
    public List<PatronCorteResponse> listarActivos(Long idTipoPrenda, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_LECTURA);

        List<PatronCorte> patrones = idTipoPrenda != null
            ? patronCorteRepository.findByActivoTrueAndTiposPrenda_IdOrderByNombreAsc(idTipoPrenda)
            : patronCorteRepository.findByActivoTrueOrderByNombreAsc();

        return patrones.stream()
            .map(patronCorteMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public PatronCorteResponse obtenerPatronCorte(Long id, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_LECTURA);

        PatronCorte patronCorte = patronCorteRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el patrón de corte con id " + id));
        return patronCorteMapper.toResponse(patronCorte);
    }
}
