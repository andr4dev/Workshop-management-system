package com.workshopmanagement.rdmotors.carga.dominio;

/** Una carga se revisa en borrador y termina de una de dos formas: entra como compra, o se descarta. */
public enum EstadoCarga {
    BORRADOR,
    CONFIRMADA,
    DESCARTADA
}
