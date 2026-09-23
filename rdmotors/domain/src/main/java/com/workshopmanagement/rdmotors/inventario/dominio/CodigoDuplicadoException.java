package com.workshopmanagement.rdmotors.inventario.dominio;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import lombok.Getter;

/**
 * El código de un repuesto ya está en uso.
 *
 * <p>Lleva <b>el nombre del repuesto que lo tiene</b>, no solo el código. Sin eso el administrador
 * recibe "código repetido" y queda sin saber si se equivocó al teclear o si esa pieza ya la había
 * creado la semana pasada — que es justo lo que necesita saber para decidir si corrige o si
 * simplemente la usa.
 */
@Getter
public class CodigoDuplicadoException extends ReglaDeNegocioException {

    private final String codigo;
    private final String nombreDelQueLoTiene;

    public CodigoDuplicadoException(String codigo, String nombreDelQueLoTiene) {
        super("El código %s ya lo usa: %s".formatted(codigo, nombreDelQueLoTiene));
        this.codigo = codigo;
        this.nombreDelQueLoTiene = nombreDelQueLoTiene;
    }
}
