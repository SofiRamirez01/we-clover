package com.weclover.backend.dto.patroncorte;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PatronCorteCreateRequest(

    @NotNull(message = "El número interno es obligatorio")
    @Positive(message = "El número interno debe ser mayor a cero")
    Integer numeroInterno,

    @NotBlank(message = "El nombre del patrón es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
    String nombre,

    @NotNull(message = "Debe seleccionar el tipo de prenda")
    Long idTipoPrenda,

    @NotNull(message = "Debe adjuntar la imagen del patrón")
    MultipartFile imagen,

    @NotNull(message = "Debe indicar los gramos de Friza por color")
    @Size(min = 1, max = 5, message = "La cantidad de colores debe estar entre 1 y 5")
    List<@NotNull(message = "Falta el valor de gramos de un color") @Positive(message = "Los gramos deben ser mayores a cero") Integer> gramosPorColor
) {
}
