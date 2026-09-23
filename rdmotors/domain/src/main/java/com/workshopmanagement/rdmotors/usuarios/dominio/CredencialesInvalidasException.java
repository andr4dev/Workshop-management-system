package com.workshopmanagement.rdmotors.usuarios.dominio;

/**
 * No se pudo entrar (spec 0004, RF-003). <b>El mensaje es el mismo</b> si el usuario no existe, si la contraseña
 * está mal o si está desactivado: no le dice a nadie qué usuarios existen.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public static final String CODIGO = "CREDENCIALES";

    public CredencialesInvalidasException() {
        super("Usuario o contraseña incorrectos");
    }
}
