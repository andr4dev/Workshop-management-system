package com.workshopmanagement.rdmotors.inventario.dominio;

/** Que le paso al inventario. El signo de la cantidad lo dice el movimiento, no este enum. */
public enum TipoMovimiento {

    /** Entrada por compra a proveedor. Es el UNICO que recalcula el costo promedio ponderado. */
    COMPRA,

    /** Salida por venta de mostrador. No toca el promedio: vender no cambia lo que costo comprar. */
    VENTA,

    /** Entrada por devolucion de cliente. No toca el promedio. */
    DEVOLUCION,

    /** Correccion manual tras conteo fisico. Solo ADMIN. */
    AJUSTE,

    /**
     * Deshace una VENTA anulada: las unidades vuelven al promedio vigente. Nunca toca el promedio.
     * No sirve para compras — ver {@link #CORRECCION_COMPRA}.
     */
    REVERSION,

    /**
     * Saca lo que metió un renglón de compra que se corrigió (spec 0002). <b>Sí recalcula el
     * promedio</b>: esas unidades entraron a su propio costo, y quitarlas tiene que quitar ese costo.
     */
    CORRECCION_COMPRA,

    /** Igual que {@link #CORRECCION_COMPRA}, cuando la compra entera se anuló. */
    ANULACION_COMPRA;

    /**
     * Las reversiones de compra salen del inventario pero no lo <b>consumen</b>: devuelven justo lo
     * que entró. Por eso no bloquean corregir otra compra del mismo repuesto (spec 0002, RF-019).
     */
    public boolean esReversionDeCompra() {
        return this == CORRECCION_COMPRA || this == ANULACION_COMPRA;
    }
}
