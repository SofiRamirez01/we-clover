package com.weclover.backend.exception;

/** Los parámetros de la solicitud no son válidos o no se pueden combinar (400). */
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String message) {
        super(message);
    }
}
