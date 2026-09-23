package com.workshopmanagement.rdmotors.caja.dominio;

/**
 * Dónde cae un gasto en el reporte de ganancia (spec 0006, decisión 4). Lo dice la categoría, una sola
 * vez: al registrar un gasto nadie decide esto de nuevo (`SPEC_Modelo_Datos.md:452-453`).
 */
public enum NaturalezaGasto {

    /** Lo que se le suma a la mercancía por fuera de la factura. Resta de la utilidad bruta. */
    COSTO,

    /** Lo que cuesta tener la tienda abierta: arriendo, luz, almuerzo. Resta de la utilidad neta. */
    GASTO
}
