package com.workshopmanagement.rdmotors.usuarios.dominio;

/** Cinco intentos fallidos seguidos: ese usuario espera cinco minutos (spec 0004, RF-003). */
public class UsuarioBloqueadoException extends RuntimeException {

    public static final String CODIGO = "BLOQUEADO";

    public UsuarioBloqueadoException() {
        super("Demasiados intentos. Espera 5 minutos y vuelve a intentarlo");
    }
}
