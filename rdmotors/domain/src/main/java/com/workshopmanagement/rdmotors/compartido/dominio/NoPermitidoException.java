package com.workshopmanagement.rdmotors.compartido.dominio;

/**
 * Alguien pidió algo que su rol no puede (spec 0004, RF-010). No cambia nada.
 *
 * <p>No es una {@link ReglaDeNegocioException}: aquello no está mal, lo está pidiendo la persona equivocada. El
 * adaptador lo traduce a 403, no a 422.
 */
public class NoPermitidoException extends RuntimeException {

    public static final String CODIGO = "NO_PERMITIDO";

    public NoPermitidoException() {
        this("No permitido: es del administrador");
    }

    public NoPermitidoException(String mensaje) {
        super(mensaje);
    }
}
