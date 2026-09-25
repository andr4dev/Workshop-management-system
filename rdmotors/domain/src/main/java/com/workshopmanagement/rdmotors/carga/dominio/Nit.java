package com.workshopmanagement.rdmotors.carga.dominio;

/**
 * El NIT impreso en una factura contra el que se guardó en la ficha del proveedor.
 *
 * <p>En la ficha se escribe de cualquier forma: {@code 900576528}, {@code 900.576.528}, {@code 900576528-1}. Se
 * comparan solo los dígitos, y el de verificación —el que va después del guion— puede estar o no.
 */
public final class Nit {

    private Nit() {
    }

    public static boolean coincide(String guardado, String impreso) {
        String a = digitos(guardado);
        String b = digitos(impreso);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return a.equals(b)
                || (a.length() == b.length() + 1 && a.startsWith(b))
                || (b.length() == a.length() + 1 && b.startsWith(a));
    }

    private static String digitos(String nit) {
        return nit == null ? "" : nit.replaceAll("\\D", "");
    }
}
