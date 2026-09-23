package com.workshopmanagement.rdmotors.inventario.dominio;

/** De que documento nacio el movimiento. Junto con el id permite rastrear el kardex hasta su causa. */
public enum OrigenMovimiento {
    COMPRA,
    VENTA,
    DEVOLUCION,
    AJUSTE_MANUAL
}
