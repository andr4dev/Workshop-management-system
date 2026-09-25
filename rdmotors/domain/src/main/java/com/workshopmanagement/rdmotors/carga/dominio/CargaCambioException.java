package com.workshopmanagement.rdmotors.carga.dominio;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Mientras se revisaba la pre-carga, el inventario cambió de una forma que cambia lo que se va a confirmar: alguien
 * creó a mano uno de esos códigos, y el renglón pasó de "nuevo" a "reposición". Se avisa <b>antes</b> de registrar
 * nada (§6 del spec), para que quien confirma vea lo que de verdad va a entrar.
 */
public class CargaCambioException extends ReglaDeNegocioException {

    public CargaCambioException(String mensaje) {
        super(mensaje);
    }
}
