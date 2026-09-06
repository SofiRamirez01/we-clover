package com.weclover.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.weclover.backend.dto.cargatalles.AlumnoCreateRequest;
import com.weclover.backend.dto.cargatalles.AlumnoResponse;
import com.weclover.backend.dto.cargatalles.CargaTallesResponse;
import com.weclover.backend.dto.cargatalles.ComboResponse;
import com.weclover.backend.dto.cargatalles.ComboUpsertRequest;
import com.weclover.backend.service.CargaTallesService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Rutas públicas de la carga de talles — sin login, protegidas solo por `token` (UUID no
 * adivinable). No hay filtro de seguridad HTTP en este proyecto (ver
 * doc/pantallas-pendientes.md, "Seguridad real JWT/Spring Security"): estas rutas ya son
 * alcanzables sin header `X-Usuario-Id` porque ninguna ruta lo exige a nivel de filtro, así que
 * acá directamente no se lo pide ni se lo usa — toda la validación de "quién puede tocar esto"
 * pasa por el token.
 */
@RestController
@RequestMapping("/api/carga-talles/{token}")
@RequiredArgsConstructor
public class CargaTallesPublicaController {

    private final CargaTallesService cargaTallesService;

    @GetMapping
    public CargaTallesResponse obtener(@PathVariable String token) {
        return cargaTallesService.obtenerPorToken(token);
    }

    @PostMapping("/alumnos")
    public ResponseEntity<AlumnoResponse> agregarAlumno(
            @PathVariable String token,
            @Valid @RequestBody AlumnoCreateRequest request) {
        AlumnoResponse response = cargaTallesService.agregarAlumno(token, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/alumnos/{idAlumno}")
    public ResponseEntity<Void> eliminarAlumno(
            @PathVariable String token,
            @PathVariable Long idAlumno) {
        cargaTallesService.eliminarAlumno(token, idAlumno);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/alumnos/{idAlumno}/productos/{idProducto}")
    public ResponseEntity<ComboResponse> agregarUnidad(
            @PathVariable String token,
            @PathVariable Long idAlumno,
            @PathVariable Long idProducto) {
        ComboResponse response = cargaTallesService.agregarUnidad(token, idAlumno, idProducto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/combos/{idCombo}")
    public ComboResponse actualizarCombo(
            @PathVariable String token,
            @PathVariable Long idCombo,
            @Valid @RequestBody ComboUpsertRequest request) {
        return cargaTallesService.actualizarCombo(token, idCombo, request);
    }

}
