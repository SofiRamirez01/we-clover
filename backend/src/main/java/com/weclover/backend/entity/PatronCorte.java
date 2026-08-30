package com.weclover.backend.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "patrones_corte")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class PatronCorte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * No es único a nivel de base ni global: dos molderías que no comparten ningún tipo de
     * prenda pueden tener el mismo número (ej. moldería #1 de Buzo/Campera y moldería #1 de
     * Chomba/Remera son numeraciones independientes). La unicidad real se valida en el
     * service, por tipo de prenda (ver PatronCorteService.validarNumeroInternoDisponible).
     */
    @Column(name = "numero_interno", nullable = false)
    private Integer numeroInterno;

    @Column(nullable = false, length = 150)
    private String nombre;

    /**
     * Un mismo patrón puede aplicar a varios tipos de prenda a la vez (ej. "Clásica" sirve
     * para Buzo y para Campera, misma moldería y mismo consumo de tela — lo único que las
     * distingue, si lleva cierre o no, ya lo define el tipo de prenda elegido en el Producto,
     * no hace falta un campo separado acá). Mínimo 1 tipo de prenda, validado en el service.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "patron_corte_tipo_prenda",
        joinColumns = @JoinColumn(name = "id_patron_corte"),
        inverseJoinColumns = @JoinColumn(name = "id_tipo_prenda")
    )
    @Builder.Default
    private List<TipoPrenda> tiposPrenda = new ArrayList<>();

    @Column(name = "imagen_url", nullable = false, length = 500)
    private String imagenUrl;

    @Column(name = "cantidad_colores", nullable = false)
    private int cantidadColores;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @OneToMany(mappedBy = "patronCorte", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orden ASC")
    @Builder.Default
    private List<PatronCorteColor> colores = new ArrayList<>();
}
