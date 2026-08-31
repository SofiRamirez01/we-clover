package com.weclover.backend.dto.stock;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record StockGuardarCambiosRequest(

    @NotEmpty(message = "Debe incluir al menos una fila")
    @Valid
    List<StockUpsertItem> items
) {
}
