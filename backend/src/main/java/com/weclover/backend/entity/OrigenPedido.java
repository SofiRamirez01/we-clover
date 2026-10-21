package com.weclover.backend.entity;

/** De dónde salió el pedido: cargado a mano o importado (Excel de Kommo o API de Kommo). */
public enum OrigenPedido {
    MANUAL,
    EXCEL,
    KOMMO
}
