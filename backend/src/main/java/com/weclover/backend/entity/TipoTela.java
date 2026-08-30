package com.weclover.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Catálogo de telas/insumos (antes era un enum de Java): la empresa va a seguir sumando
 * tipos con el tiempo (ej. Ribb, Corderito) y cada alta no debería requerir tocar código
 * ni redeployar.
 *
 * `codigo` es la clave estable para el contrato de la API y para los lookups internos que
 * antes usaban la constante del enum (ej. "buscar la fila CIERRE") — se mantiene en el
 * mismo formato que tenía el enum ("FRIZA", "JERSEY", ...) para no romper al frontend
 * actual, que sigue mandando/recibiendo ese string tal cual. `nombre` es el humano-legible
 * ("Friza", "Jersey", ...), pensado para una futura pantalla de administración, todavía sin
 * usar en el contrato de la API.
 */
@Entity
@Table(name = "tipos_tela")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class TipoTela {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    /** true = se compra/consume por peso (gramos); false = por unidad (ej. Cierre: 1 por prenda). */
    @Column(name = "es_por_peso", nullable = false)
    private boolean esPorPeso;

    /**
     * true si puede ser la tela principal del cuerpo de una prenda (Producto.tipoTela);
     * false para tipos que solo existen como insumo secundario (ProductoInsumoSecundario),
     * ej. Cierre o Ribb. Reemplaza la vieja regla hardcodeada "nunca CIERRE" de
     * ProductoService.actualizarTipoTela por una propiedad de datos.
     */
    @Column(name = "tela_cuerpo", nullable = false)
    private boolean telaCuerpo;

    /**
     * Sugerencia de cantidad al agregar este tipo como insumo secundario de un producto
     * (no aplica cuando se usa como tela de cuerpo — ahí el gramaje sale del patrón de
     * corte). Nullable: no todos los insumos tienen una sugerencia razonable.
     */
    @Column(name = "gramos_sugerido")
    private Integer gramosSugerido;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}
