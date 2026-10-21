package com.weclover.backend.exception;

/**
 * La fila/lead ya se importó antes (mismo lead de Kommo o mismo Nº de ficha). Es un salteo como
 * cualquier BusinessRuleException, pero se distingue para mostrarlo aparte de los que hay que
 * corregir (ver KommoImportService).
 */
public class PedidoYaImportadoException extends BusinessRuleException {

    public PedidoYaImportadoException(String message) {
        super(message);
    }
}
