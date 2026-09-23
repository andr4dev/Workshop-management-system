package com.workshopmanagement.rdmotors.compartido.dominio;

import java.util.Locale;

/**
 * Cómo se compara un texto al buscar: sin mayúsculas <b>y sin tildes</b>. "bujia" encuentra "BUJÍA",
 * "nandu" encuentra "ÑANDÚ", y al revés.
 *
 * <p>La usan todos los buscadores de repuestos: el inventario, las sugerencias de concepto al crear un
 * repuesto y el historial de compras por repuesto. Si uno ignorara tildes y otro no, la misma palabra
 * encontraría cosas distintas según la pantalla; en las sugerencias, además, "BUJIA" no ofrecería el
 * concepto "BUJÍA" que ya existe y el catálogo se duplicaría.
 *
 * <p><b>La base compara con estas mismas dos listas.</b> La infraestructura las registra como la
 * función SQL {@code sin_tildes} ({@code translate(lower(x), CON_TILDE, SIN_TILDE)}), así que Java y
 * Postgres no pueden quitar tildes distinto: es la misma tabla de letras. Por eso es una lista explícita
 * y no {@code Normalizer} ni la extensión {@code unaccent}, que no coinciden entre sí en todas las letras.
 *
 * <p>La eñe cuenta como tilde: en un buscador se escribe "nandu" desde un teclado que no la tiene.
 */
public final class TextoDeBusqueda {

    /** Minúsculas y mayúsculas: si la base no pasara a minúsculas una letra con tilde, igual la quita. */
    public static final String CON_TILDE = "áàäâãéèëêíìïîóòöôõúùüûñçÁÀÄÂÃÉÈËÊÍÌÏÎÓÒÖÔÕÚÙÜÛÑÇ";
    public static final String SIN_TILDE = "aaaaaeeeeiiiiooooouuuuncaaaaaeeeeiiiiooooouuuunc";

    static {
        // translate() empareja letra por letra: una lista más larga que la otra borraría letras.
        if (CON_TILDE.length() != SIN_TILDE.length()) {
            throw new IllegalStateException("Las listas de tildes no tienen el mismo largo");
        }
    }

    private TextoDeBusqueda() {
    }

    /** En minúsculas y sin tildes. {@code null} sigue siendo {@code null}. */
    public static String normalizar(String texto) {
        if (texto == null) {
            return null;
        }
        String minusculas = texto.toLowerCase(Locale.ROOT);
        StringBuilder sinTildes = new StringBuilder(minusculas.length());
        for (int i = 0; i < minusculas.length(); i++) {
            char letra = minusculas.charAt(i);
            int posicion = CON_TILDE.indexOf(letra);
            sinTildes.append(posicion < 0 ? letra : SIN_TILDE.charAt(posicion));
        }
        return sinTildes.toString();
    }

    /** Si {@code campo} contiene {@code busqueda}, sin mayúsculas ni tildes. Un campo vacío no contiene nada. */
    public static boolean contiene(String campo, String busqueda) {
        return campo != null && busqueda != null && normalizar(campo).contains(normalizar(busqueda));
    }
}
