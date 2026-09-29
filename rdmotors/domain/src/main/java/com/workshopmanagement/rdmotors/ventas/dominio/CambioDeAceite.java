package com.workshopmanagement.rdmotors.ventas.dominio;

/**
 * Lo que se escoge en el renglón de un repuesto que paga comisión por cambio de aceite (spec 0015, decisión 3). No hay
 * respuesta de entrada: el cajero escoge siempre.
 */
public enum CambioDeAceite {

    /** Se cambia en la tienda: se cobra el precio del repuesto, y la comisión sale del cajón para quien lo cambió. */
    SE_CAMBIA,

    /** El cliente se lo lleva sin cambiar: el precio baja la comisión ($65.000 − $3.000 = $62.000). */
    NO_SE_CAMBIA
}
