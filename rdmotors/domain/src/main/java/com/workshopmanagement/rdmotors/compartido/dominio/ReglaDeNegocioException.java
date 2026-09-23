package com.workshopmanagement.rdmotors.compartido.dominio;

/**
 * Una regla del negocio que se intento violar. No es un error tecnico: es el sistema diciendo
 * "eso no se puede hacer" con un mensaje que el cajero puede leer.
 *
 * <p>Vive en el dominio, no en la capa web, porque la regla es del negocio. Quien la traduce a un
 * codigo HTTP es el adaptador de {@code pos} — el dominio no sabe que existe el HTTP.
 */
public class ReglaDeNegocioException extends RuntimeException {

    public ReglaDeNegocioException(String mensaje) {
        super(mensaje);
    }
}
