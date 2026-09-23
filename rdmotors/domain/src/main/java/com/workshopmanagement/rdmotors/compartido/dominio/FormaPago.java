package com.workshopmanagement.rdmotors.compartido.dominio;

/**
 * Con qué se pagó: una compra a proveedor (spec 0002, RF-001) o una venta de mostrador (spec 0003,
 * decisión 2).
 *
 * <p>Solo dos a propósito. Nequi o Daviplata no son una forma de pago aparte. En <b>compras</b> son
 * cuentas, y una transferencia dice desde cuál salió, para que los totales por cuenta cuadren contra
 * el extracto. En <b>ventas</b> la transferencia no dice a qué cuenta llegó: al cobrar en el mostrador
 * no hace falta.
 *
 * <p>Solo el efectivo entra al cajón: es la regla del arqueo de la rebanada 3.
 */
public enum FormaPago {

    EFECTIVO,

    /** Incluye el QR. En compras exige cuenta; en ventas no. */
    TRANSFERENCIA;

    /** Lo que suma al esperado del cajón en el arqueo. */
    public boolean entraAlCajon() {
        return this == EFECTIVO;
    }
}
