package com.workshopmanagement.rdmotors.ventas.dominio;

import java.util.UUID;

import lombok.Getter;

/**
 * Dos cobros con la misma llave llegaron a la vez y la base rechazó el segundo por el índice único.
 *
 * <p>No es un error para el cajero: la venta sí se cobró, una vez. Quien la recibe responde con la
 * venta que ya quedó guardada.
 */
@Getter
public class VentaRepetidaException extends RuntimeException {

    private final UUID llave;

    public VentaRepetidaException(UUID llave) {
        super("Ya existe una venta con la llave " + llave);
        this.llave = llave;
    }
}
