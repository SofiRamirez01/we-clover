package com.weclover.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol", nullable = false)
    private Rol rol;

    @Column(nullable = false, length = 150)
    private String nombre;

    /**
     * Nullable a nivel de base (MySQL permite múltiples NULL en una columna unique): los
     * representantes de curso dados de alta por la importación de Excel (ver PedidoImportService)
     * a veces no traen email real en el origen, y ahí no hay con qué buscar/reutilizar un
     * Usuario existente por email — cada fila crea uno nuevo. El alta manual de un pedido
     * sigue exigiendo el email a nivel de DTO (@NotBlank en PedidoCreateRequest).
     */
    @Column(unique = true, length = 150)
    private String email;

    @Column(length = 30)
    private String telefono;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false)
    private boolean habilitado;
}
