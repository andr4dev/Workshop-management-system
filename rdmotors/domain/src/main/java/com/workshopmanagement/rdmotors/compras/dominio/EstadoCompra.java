package com.workshopmanagement.rdmotors.compras.dominio;

/**
 * Una compra se anula, no se borra (spec 0002, RF-023). La anulada sigue en el historial, con quién,
 * cuándo y por qué, y deja de contar en los totales.
 */
public enum EstadoCompra {
    VIGENTE,
    ANULADA
}
