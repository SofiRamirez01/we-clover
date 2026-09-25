package com.weclover.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.weclover.backend.entity.EtapaProduccion;
import com.weclover.backend.entity.Producto;
import com.weclover.backend.entity.ProductoEtapaProduccion;

@Repository
public interface ProductoEtapaProduccionRepository extends JpaRepository<ProductoEtapaProduccion, Long> {

    List<ProductoEtapaProduccion> findByProducto(Producto producto);

    Optional<ProductoEtapaProduccion> findByProductoAndEtapa(Producto producto, EtapaProduccion etapa);

    /** Condición de EN_PRODUCCION (ver EstadoPedidoService): al menos una etapa completada en
     *  algún producto del pedido. */
    boolean existsByProducto_Pedido_IdAndCompletadoTrue(Long idPedido);
}
