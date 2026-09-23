package com.workshopmanagement.rdmotors.correo.dominio;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * A quién le llega el resumen del cierre (spec 0010, RF-007): de 0 a 5 correos, sin repetir.
 *
 * <p>Vacío es válido: es la forma de apagar el correo del cierre sin tocar la configuración del servidor.
 *
 * <p>La revisión de la forma es la mínima que evita el error de dedo (<i>"ruben@gmail"</i>, <i>"ruben gmail.com"</i>),
 * no la del RFC completo: una dirección que Brevo rechace queda a la vista como correo fallido, con lo que dijo Brevo.
 */
public record Destinatarios(List<String> correos) {

    public static final int MAXIMO = 5;
    public static final int LARGO_MAXIMO = 120;
    private static final Pattern FORMA = Pattern.compile("^[^\\s@,;]+@[^\\s@,;]+\\.[^\\s@,;]{2,}$");
    private static final String SEPARADOR = ",";

    public Destinatarios {
        correos = List.copyOf(correos);
    }

    /** Desde lo que escribió el administrador: sin espacios de más, en minúsculas y sin repetidos. */
    public static Destinatarios de(List<String> escritos) {
        Set<String> limpios = new LinkedHashSet<>();
        for (String escrito : escritos == null ? List.<String>of() : escritos) {
            if (escrito == null || escrito.isBlank()) {
                continue;
            }
            String correo = escrito.strip().toLowerCase(Locale.ROOT);
            if (correo.length() > LARGO_MAXIMO || !FORMA.matcher(correo).matches()) {
                throw new ReglaDeNegocioException("«" + escrito.strip() + "» no parece un correo: revísalo");
            }
            limpios.add(correo);
        }
        if (limpios.size() > MAXIMO) {
            throw new ReglaDeNegocioException("El resumen del cierre llega a " + MAXIMO + " correos como máximo");
        }
        return new Destinatarios(new ArrayList<>(limpios));
    }

    /** Desde como se guarda en la base: separados por comas. */
    public static Destinatarios desdeTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return new Destinatarios(List.of());
        }
        return de(List.of(texto.split(SEPARADOR)));
    }

    public String comoTexto() {
        return String.join(SEPARADOR, correos);
    }

    public boolean hayAlguno() {
        return !correos.isEmpty();
    }
}
