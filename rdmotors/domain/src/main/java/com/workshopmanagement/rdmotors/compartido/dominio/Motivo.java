package com.workshopmanagement.rdmotors.compartido.dominio;

/**
 * El motivo de una acción que queda en la auditoría: corregir o anular una compra (spec 0002), anular
 * una venta o aplicar un descuento (spec 0003).
 *
 * <p>Una sola regla para todos: sin motivo, dentro de tres meses nadie sabe por qué esa factura cambió
 * o por qué esa venta salió más barata.
 */
public final class Motivo {

    /** El largo de las columnas de motivo en la base. */
    public static final int LARGO_MAXIMO = 300;

    private Motivo() {
    }

    /** Obligatorio, sin espacios de sobra y de largo razonable. Devuelve el motivo limpio. */
    public static String exigir(String motivo) {
        String limpio = motivo == null ? "" : motivo.trim();
        if (limpio.isEmpty()) {
            throw new ReglaDeNegocioException("Escribe el motivo: queda en el registro de auditoría");
        }
        if (limpio.length() > LARGO_MAXIMO) {
            throw new ReglaDeNegocioException("El motivo es muy largo: máximo " + LARGO_MAXIMO + " caracteres");
        }
        return limpio;
    }
}
