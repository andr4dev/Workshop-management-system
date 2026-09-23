package com.workshopmanagement.rdmotors.usuarios.dominio;

/**
 * Desde qué equipo se intentó entrar (spec 0004, RF-025): su dirección en la red de la tienda y qué navegador dijo
 * ser. Los dos pueden faltar —una prueba, un cliente que no los manda— y no pasa nada: el registro vale igual.
 */
public record DatosDelEquipo(String ip, String navegador) {

    public static final DatosDelEquipo NINGUNO = new DatosDelEquipo(null, null);
}
