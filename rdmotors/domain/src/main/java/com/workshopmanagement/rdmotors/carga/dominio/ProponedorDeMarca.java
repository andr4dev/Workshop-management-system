package com.workshopmanagement.rdmotors.carga.dominio;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Propone la marca de un repuesto leyendo el final de su descripción (spec 0012, RF-010).
 *
 * <p>Jotapartes escribe la marca al final: <i>"PALANCA CAMBIOS BOXER 100 <b>INOKI</b>"</i>, <i>"BUJIA CR7HSA
 * <b>NGK</b>"</i>, <i>"BALINERA 6304 C3 <b>KOYO JAPON</b>"</i>. Si la descripción termina en una marca conocida, se
 * propone esa, y el nombre del repuesto queda <b>sin</b> ella: la marca ya tiene su propio lugar, y en el mostrador
 * se lee mejor "PALANCA CAMBIOS BOXER 100 · INOKI" que la marca repetida dos veces.
 *
 * <p><b>Solo propone marcas que conoce</b>: las de la lista de abajo y las que ya estén en uso en el sistema. Adivinar
 * que la última palabra es una marca convertiría "CB110" o "12V" en marcas. Lo que no se reconoce se deja vacío, y la
 * pre-carga pide completarlo — en la MAG477 son 176 de 592 renglones.
 */
public final class ProponedorDeMarca {

    /** Las que aparecen en las facturas de Jotapartes. */
    static final List<String> SEMBRADAS = List.of(
            "INOKI", "NGK", "KOYO JAPON", "KOYO", "KANUNI", "ARX", "SUN", "CBI", "NIRIN", "DARROW", "LEO", "JAPAN",
            "JAPON", "NAL.");

    /**
     * "NAL." es la abreviatura de <i>nacional</i> en la factura; en el mostrador se entiende mejor completa. Es el
     * único cambio que se le hace a lo que dice el proveedor.
     */
    private static final Map<String, String> COMO_SE_GUARDA = Map.of("NAL.", "NACIONAL");

    /** Las más largas primero: "KOYO JAPON" tiene que ganarle a "JAPON". */
    private final List<String> conocidas;

    public ProponedorDeMarca(Collection<String> enUso) {
        Set<String> todas = new LinkedHashSet<>(SEMBRADAS);
        for (String marca : enUso) {
            if (marca != null && !marca.isBlank()) {
                todas.add(normalizar(marca));
            }
        }
        this.conocidas = todas.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    /**
     * @return la marca y el nombre que queda sin ella, o vacío si la descripción no termina en una marca conocida
     */
    public Optional<Propuesta> proponer(String descripcion) {
        if (descripcion == null || descripcion.isBlank()) {
            return Optional.empty();
        }
        String limpia = normalizar(descripcion);
        for (String marca : conocidas) {
            if (limpia.endsWith(" " + marca)) {
                String nombre = limpia.substring(0, limpia.length() - marca.length()).strip();
                return Optional.of(new Propuesta(COMO_SE_GUARDA.getOrDefault(marca, marca), nombre));
            }
        }
        return Optional.empty();
    }

    /** Mayúsculas y un solo espacio entre palabras, que es como llegan del PDF y como se guardan. */
    static String normalizar(String texto) {
        return texto.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /**
     * @param marca          como se guarda: "INOKI", "NACIONAL"
     * @param nombreSinMarca la descripción sin la marca del final, para el nombre del repuesto
     */
    public record Propuesta(String marca, String nombreSinMarca) {
    }
}
