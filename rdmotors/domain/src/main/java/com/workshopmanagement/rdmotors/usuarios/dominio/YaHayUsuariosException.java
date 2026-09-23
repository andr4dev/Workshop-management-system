package com.workshopmanagement.rdmotors.usuarios.dominio;

/** El primer administrador ya existe: crearlo de nuevo no es una opción (spec 0004, RF-018). */
public class YaHayUsuariosException extends RuntimeException {

    public static final String CODIGO = "YA_INSTALADO";

    public YaHayUsuariosException() {
        super("El sistema ya tiene administrador: entra con tu usuario");
    }
}
