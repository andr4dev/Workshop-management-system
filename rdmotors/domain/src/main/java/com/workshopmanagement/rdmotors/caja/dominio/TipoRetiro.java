package com.workshopmanagement.rdmotors.caja.dominio;

/**
 * De dónde sale la plata de un retiro (2026-10-09). El cajón y la cuenta de transferencias se cuadran por separado: un
 * retiro resta en uno, en el otro, o pasa plata de uno al otro.
 */
public enum TipoRetiro {

    /** Sale del cajón y se va: para el dueño, un pago, lo que sea. Es el retiro de siempre. */
    DEL_CAJON,

    /** Sale del cajón y entra a la cuenta de transferencias: el efectivo que se consigna en Daviplata. */
    A_TRANSFERENCIAS,

    /** Sale de la cuenta de transferencias: plata que se saca de Daviplata para el dueño u otra cosa. */
    DE_TRANSFERENCIAS;

    /** ¿Resta en el cajón? */
    public boolean saleDelCajon() {
        return this == DEL_CAJON || this == A_TRANSFERENCIAS;
    }
}
