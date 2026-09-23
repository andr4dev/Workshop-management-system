package com.workshopmanagement.rdmotors.clientes.dominio;

/** De dónde sale una deuda (spec 0008). */
public enum OrigenDeuda {

    /** Una venta que quedó fiada, toda o una parte. */
    VENTA,

    /** Lo que el cliente ya debía en el cuaderno, cargado una sola vez (RF-028). */
    CUADERNO
}
