package com.workshopmanagement.rdmotors.ventas.dominio;

/**
 * Cómo se capturó un descuento. <b>Es el registro de la intención, no el dato:</b> lo que se guarda
 * y lo que manda es siempre el monto en pesos (`SPEC_Modelo_Datos.md` §3.5).
 */
public enum ModoDescuento {
    /** "Te lo dejo en $35.000": se escribió cuántos pesos. */
    MONTO,
    /** "Te hago el 10%": se escribió un porcentaje, y de él salió el monto. */
    PORCENTAJE
}
