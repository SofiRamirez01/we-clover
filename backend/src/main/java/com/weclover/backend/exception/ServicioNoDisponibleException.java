package com.weclover.backend.exception;

/** Un servicio externo del que depende la operación (ej. el optimizador de cortes) no respondió. */
public class ServicioNoDisponibleException extends RuntimeException {

    public ServicioNoDisponibleException(String message) {
        super(message);
    }
}
