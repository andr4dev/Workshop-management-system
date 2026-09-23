package com.workshopmanagement.rdmotors.ventas.dominio;

/**
 * En la base una venta nace cobrada: la que se está armando vive en el navegador y no tiene número
 * (spec 0003, RF-028). Por eso no hay un estado "en curso".
 */
public enum EstadoVenta {
    COBRADA,
    /** Se deshizo con un motivo; su número no se reutiliza. Llega en la fase 5. */
    ANULADA
}
