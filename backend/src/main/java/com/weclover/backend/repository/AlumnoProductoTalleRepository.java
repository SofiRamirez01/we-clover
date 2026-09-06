package com.weclover.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weclover.backend.entity.AlumnoPedido;
import com.weclover.backend.entity.AlumnoProductoTalle;
import com.weclover.backend.entity.CargaTallesPedido;
import com.weclover.backend.entity.Producto;

public interface AlumnoProductoTalleRepository extends JpaRepository<AlumnoProductoTalle, Long> {

    /** Todos los combos de todos los alumnos de una carga, para armar la respuesta completa sin
     *  N+1 por alumno. */
    List<AlumnoProductoTalle> findByAlumnoPedido_CargaTalles(CargaTallesPedido cargaTalles);

    /** Los combos de un alumno puntual — para borrarlos todos antes de borrar al alumno (no hay
     *  cascada de borrado modelada entre AlumnoPedido y AlumnoProductoTalle). */
    List<AlumnoProductoTalle> findByAlumnoPedido(AlumnoPedido alumnoPedido);

    /** Para validar que un combo puntual pertenece a la carga del token antes de editarlo o
     *  borrarlo (rutas públicas — no hay otra forma de "pertenencia" que chequear acá). */
    Optional<AlumnoProductoTalle> findByIdAndAlumnoPedido_CargaTalles(Long id, CargaTallesPedido cargaTalles);

    /** Para el resumen "18/20 unidades cargadas" por Producto — cuenta unidades (filas), no
     *  alumnos distintos, porque un mismo alumno puede aportar más de una. */
    int countByProducto(Producto producto);
}
