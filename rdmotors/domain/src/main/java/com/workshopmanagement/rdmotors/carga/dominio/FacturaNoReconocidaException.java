package com.workshopmanagement.rdmotors.carga.dominio;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * El archivo no se puede leer, o no tiene un diseño que se conozca (spec 0012, §6).
 *
 * <p>Es una regla de negocio y no un error del sistema: <b>no leer a ciegas</b> es la decisión. Un PDF de otro
 * proveedor se rechaza en vez de intentarse "a ver qué sale", porque un sistema que a veces lee mal una factura es
 * peor que uno que no la lee.
 */
public class FacturaNoReconocidaException extends ReglaDeNegocioException {

    public FacturaNoReconocidaException(String mensaje) {
        super(mensaje);
    }
}
