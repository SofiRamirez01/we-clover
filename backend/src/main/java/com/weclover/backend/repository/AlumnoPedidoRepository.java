package com.weclover.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weclover.backend.entity.AlumnoPedido;
import com.weclover.backend.entity.CargaTallesPedido;

public interface AlumnoPedidoRepository extends JpaRepository<AlumnoPedido, Long> {

    List<AlumnoPedido> findByCargaTallesOrderByOrdenAsc(CargaTallesPedido cargaTalles);

    int countByCargaTalles(CargaTallesPedido cargaTalles);
}
