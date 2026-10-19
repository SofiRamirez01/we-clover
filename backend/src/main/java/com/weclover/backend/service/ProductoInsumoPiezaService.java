package com.weclover.backend.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.producto.AsignacionInsumoPiezaItemRequest;
import com.weclover.backend.dto.producto.AsignacionesInsumoPiezaRequest;
import com.weclover.backend.dto.producto.AsignacionesInsumoResponse;
import com.weclover.backend.dto.producto.GrupoAsignacionInsumoResponse;
import com.weclover.backend.dto.producto.PinInsumoResponse;
import com.weclover.backend.entity.PatronCortePosicionPieza;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoInsumoPieza;
import com.weclover.backend.entity.ProductoInsumoSecundario;
import com.weclover.backend.entity.TipoInsumo;
import com.weclover.backend.exception.BusinessRuleException;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.repository.ProductoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Lectura y guardado de qué fila de insumo de la ficha cubre cada pin de la moldería de un
 * producto. Las reglas (qué pines son de cada tipo, cuándo hace falta asignar, cuáles faltan)
 * viven en AsignacionInsumoPieza; acá solo se valida y se persiste.
 */
@Service
@RequiredArgsConstructor
public class ProductoInsumoPiezaService {

    /** Mismos roles que cargan el resto de la ficha técnica (ver ProductoService). */
    private static final Set<String> ROLES_CARGA_DISENIO = Set.of(
        "ROLE_ADMINISTRATIVO", "ROLE_VENDEDOR", "ROLE_DISENADOR"
    );

    private final ProductoRepository productoRepository;
    private final AutorizacionService autorizacionService;

    /** Sin chequeo de rol, igual que el resto de la lectura de Ficha Técnica. */
    @Transactional(readOnly = true)
    public AsignacionesInsumoResponse obtener(Long idProducto) {
        return construirRespuesta(obtenerProducto(idProducto));
    }

    /**
     * Reemplaza todas las asignaciones del producto. Puede quedar incompleto (pines sin
     * asignar): eso se informa en la respuesta como aviso, no se rechaza.
     */
    @Transactional
    public AsignacionesInsumoResponse guardar(Long idProducto, AsignacionesInsumoPiezaRequest request, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_CARGA_DISENIO);

        Producto producto = obtenerProducto(idProducto);

        Map<Long, PatronCortePosicionPieza> pinesPorId = AsignacionInsumoPieza.pines(producto).stream()
            .collect(Collectors.toMap(PatronCortePosicionPieza::getId, Function.identity()));
        Map<Long, ProductoInsumoSecundario> filasPorId = producto.getInsumosSecundarios().stream()
            .collect(Collectors.toMap(ProductoInsumoSecundario::getId, Function.identity()));

        Set<Long> pinesRecibidos = new HashSet<>();
        for (AsignacionInsumoPiezaItemRequest item : request.asignaciones()) {
            PatronCortePosicionPieza pin = pinesPorId.get(item.idPosicionPieza());
            if (pin == null) {
                throw new BusinessRuleException(
                    "El pin " + item.idPosicionPieza() + " no pertenece a la moldería de este producto");
            }
            ProductoInsumoSecundario fila = filasPorId.get(item.idProductoInsumo());
            if (fila == null) {
                throw new BusinessRuleException(
                    "El insumo " + item.idProductoInsumo() + " no pertenece a este producto");
            }
            if (!pinesRecibidos.add(pin.getId())) {
                throw new BusinessRuleException(
                    "El pin \"" + AsignacionInsumoPieza.etiqueta(producto, pin) + "\" está asignado más de una vez");
            }
            TipoInsumo tipoDelPin = pin.getPieza().getTipoInsumo();
            if (tipoDelPin == null) {
                throw new BusinessRuleException(
                    "La pieza \"" + pin.getPieza().getNombre() + "\" es del cuerpo de la prenda: no se asigna a un insumo");
            }
            if (!TipoInsumoCodigos.esDeTipo(fila, tipoDelPin.getCodigo())) {
                throw new BusinessRuleException(
                    "La pieza \"" + pin.getPieza().getNombre() + "\" es de " + tipoDelPin.getNombre()
                        + " y no se puede asignar a \"" + fila.getDescripcion() + "\"");
            }
        }

        // Flush intermedio por el mismo motivo que ProductoService.asignarColores: la unique
        // (producto, pin) se violaría si un pin pasa de una fila a otra y Hibernate inserta la
        // asignación nueva antes de borrar la vieja.
        producto.getInsumosSecundarios().forEach(fila -> fila.getAsignacionesPieza().clear());
        productoRepository.saveAndFlush(producto);

        for (AsignacionInsumoPiezaItemRequest item : request.asignaciones()) {
            ProductoInsumoSecundario fila = filasPorId.get(item.idProductoInsumo());
            fila.getAsignacionesPieza().add(ProductoInsumoPieza.builder()
                .producto(producto)
                .productoInsumo(fila)
                .posicionPieza(pinesPorId.get(item.idPosicionPieza()))
                .build());
        }

        return construirRespuesta(productoRepository.save(producto));
    }

    private Producto obtenerProducto(Long idProducto) {
        return productoRepository.findById(idProducto)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el producto con id " + idProducto));
    }

    private AsignacionesInsumoResponse construirRespuesta(Producto producto) {
        List<GrupoAsignacionInsumoResponse> grupos = new ArrayList<>();
        int totalSinAsignar = 0;

        for (Map.Entry<TipoInsumo, List<PatronCortePosicionPieza>> entrada : AsignacionInsumoPieza.pinesPorTipo(producto).entrySet()) {
            TipoInsumo tipo = entrada.getKey();
            boolean requiereAsignacion = AsignacionInsumoPieza.requiereAsignacion(producto, tipo);

            List<PinInsumoResponse> pines = new ArrayList<>();
            int sinAsignar = 0;
            for (PatronCortePosicionPieza pin : entrada.getValue()) {
                Optional<ProductoInsumoSecundario> fila = AsignacionInsumoPieza.filaAsignada(producto, pin);
                if (requiereAsignacion && fila.isEmpty()) {
                    sinAsignar++;
                }
                pines.add(new PinInsumoResponse(
                    pin.getId(),
                    AsignacionInsumoPieza.etiqueta(producto, pin),
                    pin.getPieza().getId(),
                    pin.getPieza().getNombre(),
                    fila.map(ProductoInsumoSecundario::getId).orElse(null)));
            }

            grupos.add(new GrupoAsignacionInsumoResponse(
                tipo.getId(),
                tipo.getCodigo(),
                tipo.getNombre(),
                requiereAsignacion,
                AsignacionInsumoPieza.filasDeTipo(producto, tipo).stream().map(ProductoInsumoSecundario::getId).toList(),
                pines,
                sinAsignar));
            totalSinAsignar += sinAsignar;
        }

        return new AsignacionesInsumoResponse(producto.getId(), grupos, totalSinAsignar);
    }
}
