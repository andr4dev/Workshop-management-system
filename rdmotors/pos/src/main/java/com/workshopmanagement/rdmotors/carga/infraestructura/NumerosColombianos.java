package com.workshopmanagement.rdmotors.carga.infraestructura;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * Los números como los escribe Colombia (spec 0012, RF-002).
 *
 * <p>En una factura colombiana <b>{@code $12.943} son doce mil novecientos cuarenta y tres</b>: el punto separa los
 * miles y la coma los decimales. Leído a la inglesa serían doce pesos con noventa y cuatro, y el costo de ese renglón
 * quedaría mil veces más bajo. Es la clase de error que no revienta nada: pasa, y aparece meses después como un
 * margen imposible en un reporte.
 *
 * <p>Lo que no se entiende devuelve {@code null} — nunca un cero. La pre-carga lo muestra marcado.
 *
 * <p><b>Las celdas numéricas de un Excel no pasan por aquí</b>: ya son un número, y reinterpretarlas como texto
 * convertiría un 12,943 de verdad en doce mil.
 */
final class NumerosColombianos {

    /** 12.943 · 1.234.567 · 12,943 — grupos de a tres: separadores de miles, con el signo que sea. */
    private static final Pattern MILES = Pattern.compile("-?\\d{1,3}([.,]\\d{3})+");
    private static final Pattern ENTERO = Pattern.compile("-?\\d+");
    private static final Pattern DECIMAL = Pattern.compile("-?\\d+[.,]\\d+");

    private NumerosColombianos() {
    }

    /**
     * Plata, en pesos enteros: {@code $12.943}, {@code 12943}, {@code 12.943,50}. Los centavos se redondean al peso,
     * como todo {@link Dinero}.
     */
    static Dinero pesos(String texto) {
        String limpio = limpiar(texto);
        if (limpio == null) {
            return null;
        }
        if (ENTERO.matcher(limpio).matches() || MILES.matcher(limpio).matches()) {
            // En plata no hay miles ambiguos: $12,943 tampoco son doce pesos.
            return Dinero.de(new BigDecimal(limpio.replace(".", "").replace(",", "")));
        }
        BigDecimal numero = numero(limpio);
        return numero == null ? null : Dinero.de(numero);
    }

    /**
     * Un número que puede tener decimales —un porcentaje, una cantidad—: la coma es el decimal. Con punto y coma a la
     * vez, el último de los dos es el decimal ({@code 1.234,5} o {@code 1,234.5}).
     */
    static BigDecimal numero(String texto) {
        String limpio = limpiar(texto);
        if (limpio == null) {
            return null;
        }
        int punto = limpio.lastIndexOf('.');
        int coma = limpio.lastIndexOf(',');
        String normal;
        if (punto >= 0 && coma >= 0) {
            normal = punto > coma
                    ? limpio.replace(",", "")
                    : limpio.replace(".", "").replace(',', '.');
        } else if (coma >= 0) {
            normal = limpio.indexOf(',') == coma ? limpio.replace(',', '.') : limpio.replace(",", "");
        } else if (punto >= 0 && MILES.matcher(limpio).matches()) {
            normal = limpio.replace(".", "");
        } else {
            normal = limpio;
        }
        return ENTERO.matcher(normal).matches() || DECIMAL.matcher(normal).matches() ? new BigDecimal(normal) : null;
    }

    /** Un entero sin decimales. {@code "2"} sí; {@code "2,5"} no es una cantidad de repuestos. */
    static Integer entero(String texto) {
        BigDecimal n = numero(texto);
        if (n == null || n.stripTrailingZeros().scale() > 0) {
            return null;
        }
        try {
            return n.intValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /** Sin signo de pesos, sin espacios —tampoco el espacio que no se parte, que Excel pone entre $ y el número—. */
    private static String limpiar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.replace("$", "").replace("COP", "").replace("%", "")
                .replace('\u00A0', ' ').replace(" ", "").strip();
        return limpio.isEmpty() ? null : limpio;
    }
}
