package com.workshopmanagement.rdmotors.usuarios.dominio;

import java.security.SecureRandom;

/**
 * La contraseña que el administrador le dicta a alguien al restablecérsela (spec 0004, RF-017 y RF-019). Se usa una
 * vez: al entrar con ella, la persona elige la suya.
 *
 * <p>Ocho caracteres, sin los que se confunden al leerlos en voz alta o en la consola (ni {@code l}, {@code 1},
 * {@code o}, {@code 0} ni {@code i}), y en minúscula: se dicta por teléfono o se copia de una pantalla.
 */
public final class ContrasenaTemporal {

    public static final int LARGO = 8;
    private static final String ALFABETO = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom AZAR = new SecureRandom();

    private ContrasenaTemporal() {
    }

    public static String nueva() {
        StringBuilder texto = new StringBuilder(LARGO);
        for (int i = 0; i < LARGO; i++) {
            texto.append(ALFABETO.charAt(AZAR.nextInt(ALFABETO.length())));
        }
        return texto.toString();
    }
}
