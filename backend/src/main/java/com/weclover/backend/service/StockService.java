package com.weclover.backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weclover.backend.dto.stock.StockResponse;
import com.weclover.backend.dto.stock.StockUpsertItem;
import com.weclover.backend.entity.ArticuloStock;
import com.weclover.backend.entity.PaletaColores;
import com.weclover.backend.entity.Proveedor;
import com.weclover.backend.entity.Stock;
import com.weclover.backend.entity.Usuario;
import com.weclover.backend.exception.ResourceNotFoundException;
import com.weclover.backend.mapper.StockMapper;
import com.weclover.backend.repository.ArticuloStockRepository;
import com.weclover.backend.repository.PaletaColoresRepository;
import com.weclover.backend.repository.ProveedorRepository;
import com.weclover.backend.repository.StockRepository;
import com.weclover.backend.repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Registro de stock (Fase 1): un "valor actual" por artículo y proveedor, pensado como una
 * auditoría física periódica — cargar una cantidad nueva pisa la anterior, no se suma. No es un
 * historial de movimientos ni se guarda log de valores anteriores.
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private static final Set<String> ROLES_GESTION_STOCK = Set.of("ROLE_ADMINISTRATIVO");

    private final StockRepository stockRepository;
    private final ArticuloStockRepository articuloStockRepository;
    private final PaletaColoresRepository paletaColoresRepository;
    private final ProveedorRepository proveedorRepository;
    private final UsuarioRepository usuarioRepository;
    private final StockMapper stockMapper;
    private final AutorizacionService autorizacionService;

    @Transactional(readOnly = true)
    public List<StockResponse> listar() {
        return stockRepository.listarTodoParaGrilla().stream()
            .map(stockMapper::toResponse)
            .toList();
    }

    /** Todo o nada: por cada item, resuelve (o crea) el ArticuloStock del color y actualiza o
     *  crea la fila de Stock para (articulo, proveedor). */
    @Transactional
    public List<StockResponse> guardarCambios(List<StockUpsertItem> items, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_STOCK);
        Usuario actor = usuarioRepository.findById(idUsuarioActor)
            .orElseThrow(() -> new ResourceNotFoundException("No existe el usuario con id " + idUsuarioActor));

        List<Stock> guardados = new ArrayList<>();
        for (StockUpsertItem item : items) {
            guardados.add(guardarUnItem(item, actor));
        }
        return guardados.stream().map(stockMapper::toResponse).toList();
    }

    private Stock guardarUnItem(StockUpsertItem item, Usuario actor) {
        PaletaColores paletaColor = paletaColoresRepository.findById(item.idPaletaColor())
            .orElseThrow(() -> new ResourceNotFoundException("No existe el color con id " + item.idPaletaColor()));

        ArticuloStock articulo = articuloStockRepository.findByPaletaColor(paletaColor)
            .orElseGet(() -> articuloStockRepository.save(ArticuloStock.builder().paletaColor(paletaColor).build()));

        Proveedor proveedor = item.idProveedor() != null
            ? proveedorRepository.findById(item.idProveedor())
                .orElseThrow(() -> new ResourceNotFoundException("No existe el proveedor con id " + item.idProveedor()))
            : null;

        Stock fila = buscarFilaExistente(articulo, proveedor)
            .orElseGet(() -> Stock.builder().articulo(articulo).proveedor(proveedor).build());

        fila.setCantidad(item.cantidad());
        fila.setFechaUltimaActualizacion(LocalDateTime.now());
        fila.setActualizadoPor(actor);
        return stockRepository.save(fila);
    }

    /** proveedor null busca la fila "sin proveedor" del artículo — este lookup, antes de
     *  insertar, es lo que hace cumplir "a lo sumo una fila sin proveedor por artículo" (ver
     *  comentario de Stock.proveedor: la unique constraint de la tabla no cubre ese caso). */
    private Optional<Stock> buscarFilaExistente(ArticuloStock articulo, Proveedor proveedor) {
        return proveedor != null
            ? stockRepository.findByArticuloAndProveedor(articulo, proveedor)
            : stockRepository.findByArticuloAndProveedorIsNull(articulo);
    }

    @Transactional
    public void eliminar(Long id, Long idUsuarioActor) {
        autorizacionService.verificarRolPermitido(idUsuarioActor, ROLES_GESTION_STOCK);
        Stock fila = stockRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("No existe la fila de stock con id " + id));
        stockRepository.delete(fila);
    }
}
