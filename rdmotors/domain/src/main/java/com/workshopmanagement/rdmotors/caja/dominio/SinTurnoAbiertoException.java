package com.workshopmanagement.rdmotors.caja.dominio;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Se intentó vender (o anular) sin un turno abierto. <b>Sin turno no se vende</b>: quedaría una venta
 * fuera de todo arqueo (`SPEC_Modelo_Datos.md` §5, regla 2).
 */
public class SinTurnoAbiertoException extends ReglaDeNegocioException {

    public SinTurnoAbiertoException() {
        super("No hay un turno abierto. Ábrelo para vender.");
    }

    /** Con el mensaje de otra acción que también necesita turno, como anular. */
    public SinTurnoAbiertoException(String mensaje) {
        super(mensaje);
    }
}
