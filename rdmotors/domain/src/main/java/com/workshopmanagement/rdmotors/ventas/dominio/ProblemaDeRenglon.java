package com.workshopmanagement.rdmotors.ventas.dominio;

import java.util.UUID;

/**
 * Por qué un renglón no se puede cobrar tal como está. La pantalla marca cada renglón con su
 * problema en vez de dejar al cajero adivinando.
 *
 * @param precioVisto  el que tenía la pantalla ({@code PRECIO_CAMBIADO})
 * @param precioActual el vigente ({@code PRECIO_CAMBIADO}, {@code SIN_PRECIO})
 * @param disponible   cuántos hay ({@code SIN_STOCK})
 * @param pedido       cuántos se pidieron ({@code SIN_STOCK})
 */
public record ProblemaDeRenglon(
        UUID varianteId,
        String codigo,
        Tipo tipo,
        Long precioVisto,
        Long precioActual,
        Integer disponible,
        Integer pedido) {

    public enum Tipo {
        /** El repuesto no existe (la venta se armó con un id que ya no está). */
        NO_EXISTE,
        /** Se desactivó: no se vende. */
        INACTIVO,
        /** Tiene precio $0: hay que fijarlo en su ficha antes de venderlo. */
        SIN_PRECIO,
        /** El precio cambió mientras se armaba la venta. Nunca se cobra un precio distinto al visto. */
        PRECIO_CAMBIADO,
        /** No hay suficientes. */
        SIN_STOCK
    }

    public static ProblemaDeRenglon noExiste(UUID varianteId) {
        return new ProblemaDeRenglon(varianteId, null, Tipo.NO_EXISTE, null, null, null, null);
    }

    public static ProblemaDeRenglon de(UUID varianteId, String codigo, Tipo tipo) {
        return new ProblemaDeRenglon(varianteId, codigo, tipo, null, null, null, null);
    }

    public static ProblemaDeRenglon precioCambiado(UUID varianteId, String codigo, long visto, long actual) {
        return new ProblemaDeRenglon(varianteId, codigo, Tipo.PRECIO_CAMBIADO, visto, actual, null, null);
    }

    public static ProblemaDeRenglon sinStock(UUID varianteId, String codigo, int disponible, int pedido) {
        return new ProblemaDeRenglon(varianteId, codigo, Tipo.SIN_STOCK, null, null, disponible, pedido);
    }
}
