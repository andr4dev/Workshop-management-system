package com.workshopmanagement.rdmotors.carga.dominio;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * El IVA de la factura repartido entre sus renglones, en proporción a su valor (spec 0012, RF-004a).
 *
 * <h2>Las partes tienen que sumar el total</h2>
 *
 * Sumarle el 19% a cada renglón por separado y redondear a pesos no da el total de la factura: cada renglón pierde
 * o gana una fracción, y en 592 renglones eso se acumula en cientos de pesos. La compra quedaría por una cifra que
 * no es la que salió del banco.
 *
 * <h2>Por qué restos mayores, y no "el sobrante al más caro"</h2>
 *
 * {@code reportes.dominio.RepartoDeDescuento} le carga todo el sobrante al renglón más caro. En una venta de tres
 * renglones eso es un peso o dos, y está bien. En una factura de 592 son <b>unos 300 pesos sobre un solo
 * repuesto</b> —en la MAG477, justo la bujía iridium—, que quedaría con un costo inflado sin razón.
 *
 * Aquí cada renglón recibe su parte hacia abajo, y los pesos que sobran se reparten <b>de a uno</b>, a los renglones
 * a los que el redondeo les quitó más. Ningún renglón queda a más de un peso de su parte exacta, y la suma cuadra
 * al peso.
 */
public final class RepartoDelIva {

    private RepartoDelIva() {
    }

    /**
     * @param totales el valor total de cada renglón, sin IVA, en su orden
     * @param iva     el IVA de toda la factura
     * @return el total de cada renglón con su parte de IVA, en el mismo orden. Suman {@code Σ totales + iva}
     */
    public static List<Dinero> conIva(List<Dinero> totales, Dinero iva) {
        if (totales.isEmpty()) {
            return List.of();
        }
        if (iva.esNegativo()) {
            throw new IllegalArgumentException("Un IVA negativo no se reparte: " + iva);
        }
        BigDecimal subtotal = totales.stream().map(Dinero::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (subtotal.signum() <= 0 || iva.esCero()) {
            return List.copyOf(totales);
        }

        int n = totales.size();
        long[] partes = new long[n];
        BigDecimal[] restos = new BigDecimal[n];
        long repartido = 0;
        for (int i = 0; i < n; i++) {
            BigDecimal exacta = iva.valor().multiply(totales.get(i).valor());
            BigDecimal[] division = exacta.divideAndRemainder(subtotal);
            partes[i] = division[0].longValueExact();
            restos[i] = division[1];
            repartido += partes[i];
        }

        // Los pesos que faltan van de a uno a los de mayor resto. A igual resto, el primero: así el resultado es el
        // mismo cada vez que se calcula, y la pre-carga no cambia sola al recargarla.
        long faltan = iva.valor().longValueExact() - repartido;
        IntStream.range(0, n).boxed()
                .sorted(Comparator.comparing((Integer i) -> restos[i]).reversed().thenComparing(i -> i))
                .limit(faltan)
                .forEach(i -> partes[i]++);

        List<Dinero> conIva = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            conIva.add(Dinero.de(totales.get(i).valor().add(BigDecimal.valueOf(partes[i]))));
        }
        return conIva;
    }
}
