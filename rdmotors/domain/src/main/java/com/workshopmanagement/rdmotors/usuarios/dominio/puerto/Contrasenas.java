package com.workshopmanagement.rdmotors.usuarios.dominio.puerto;

/**
 * PUERTO — el hash de las contraseñas (spec 0004, RF-002).
 *
 * <p>Es puerto porque tiene dos implementaciones: la de verdad, lenta a propósito (probar contraseñas contra una
 * copia robada de la base tiene que ser caro), y la de las pruebas del dominio, instantánea.
 */
public interface Contrasenas {

    /** Cada vez da un hash distinto para la misma contraseña: dos personas con la misma no guardan lo mismo. */
    String hash(String contrasena);

    boolean coincide(String contrasena, String hash);
}
