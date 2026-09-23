package com.workshopmanagement.rdmotors.caja.dominio;

import java.util.UUID;

import lombok.Getter;

/**
 * Dos registros con la misma llave (un gasto o un retiro) llegaron a la vez, y la base rechazó el
 * segundo por el índice único.
 *
 * <p>No es un error para el cajero: el movimiento sí quedó, una vez. Quien la recibe responde con el que
 * ya está guardado.
 */
@Getter
public class MovimientoRepetidoException extends RuntimeException {

    private final UUID llave;

    public MovimientoRepetidoException(UUID llave) {
        super("Ya existe un movimiento con la llave " + llave);
        this.llave = llave;
    }
}
