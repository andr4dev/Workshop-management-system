package com.workshopmanagement.rdmotors.respaldo.dominio;

/**
 * El volcado no se pudo hacer: no está {@code pg_dump}, no hay permiso en la carpeta, no cabe en el disco, o el
 * motor respondió con error.
 *
 * <p><b>No frena el mostrador.</b> Quien la atrapa es {@code HacerRespaldo}, que la convierte en una fila con su
 * error para que el administrador la vea al entrar (spec 0009, RF-006).
 */
public class RespaldoFallidoException extends RuntimeException {

    public RespaldoFallidoException(String mensaje) {
        super(mensaje);
    }

    public RespaldoFallidoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
