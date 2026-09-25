package com.workshopmanagement.rdmotors.carga.dominio;

/**
 * Algo que hay que arreglar antes de confirmar una carga, o que conviene mirar (spec 0012, §6).
 *
 * <p>El mensaje va listo para mostrarse, con los números a la vista: <i>"8 × $46.993 − 18% da $308.274, pero la
 * factura dice $380.274"</i> se corrige mirando el papel; <i>"no cuadra"</i> a secas obliga a hacer la cuenta.
 */
public record Problema(Tipo tipo, String mensaje) {

    public enum Tipo {
        // ── De un renglón: no dejan confirmar ────────────────────────────────
        SIN_CODIGO,
        CODIGO_LARGO,
        SIN_DESCRIPCION,
        NOMBRE_LARGO,
        CANTIDAD_INVALIDA,
        TOTAL_INVALIDO,
        NO_CUADRA,
        CODIGO_REPETIDO,
        SIN_MARCA,
        MARCA_LARGA,
        SIN_CATEGORIA,
        SIN_PRECIO,

        // ── De un renglón: avisos, que se ven y no impiden ───────────────────
        PRECIO_BAJO_COSTO,

        // ── De la carga entera ───────────────────────────────────────────────
        SIN_PROVEEDOR,
        SIN_FECHA,
        SIN_FORMA_PAGO,
        SIN_CUENTA,
        SIN_SUBTOTAL,
        SUMA_NO_CUADRA,
        SIN_RENGLONES,
        RENGLONES_CON_PROBLEMA
    }
}
