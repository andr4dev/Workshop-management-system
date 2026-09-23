package com.workshopmanagement.rdmotors.clientes.dominio;

/** Cómo va una deuda, como lo dice la Cartera (spec 0008, RF-019). */
public enum EstadoDeuda {

    /** No se le ha abonado nada. */
    PENDIENTE,

    /** Se le abonó una parte. */
    ABONADA,

    /** Se pagó toda. */
    PAGADA,

    /** Su venta se anuló: ya no se debe. */
    ANULADA
}
