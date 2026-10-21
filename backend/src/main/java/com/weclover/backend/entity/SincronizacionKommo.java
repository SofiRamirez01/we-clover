package com.weclover.backend.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

/**
 * Una importación real desde la API de Kommo (POST /api/pedidos/importar-kommo con
 * simular=false); las simulaciones no se registran. Guarda qué se pidió (rango de fechas o ids de
 * leads) y cuántos leads se importaron, se saltearon o se descartaron por no tener Ficha.
 */
@Entity
@Table(name = "sincronizacion_kommo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class SincronizacionKommo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    /** Rango de última actualización de los leads; null si se sincronizó por ids. */
    private LocalDate desde;

    private LocalDate hasta;

    /** Ids de leads pedidos explícitamente (sincronización por ids); vacía si fue por fechas. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "sincronizacion_kommo_lead", joinColumns = @JoinColumn(name = "id_sincronizacion"))
    @Column(name = "id_lead", nullable = false)
    @Builder.Default
    private List<Long> idsLeads = new ArrayList<>();

    @Column(nullable = false)
    private int importados;

    @Column(nullable = false)
    private int salteados;

    @Column(name = "sin_ficha", nullable = false)
    private int sinFicha;
}
