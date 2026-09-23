package com.workshopmanagement.rdmotors.compras.dominio;

/**
 * Como escribio el usuario el costo de la linea. La misma factura trae renglones de las dos
 * formas, por eso el modo es <b>por linea y no por compra</b>.
 *
 * <p>Se guarda aunque sea redundante —las dos cifras quedan persistidas igual— porque saber
 * <i>como se capturo</i> explica de donde salio el numero cuando alguien revise la compra meses
 * despues.
 */
public enum ModoCaptura {

    /** "Me llegaron 20 y pague $200.000." El sistema calcula a cuanto sale cada uno. */
    TOTAL,

    /** "Me llegaron 20 a $10.000 cada uno." El sistema calcula el total. */
    UNITARIO
}
