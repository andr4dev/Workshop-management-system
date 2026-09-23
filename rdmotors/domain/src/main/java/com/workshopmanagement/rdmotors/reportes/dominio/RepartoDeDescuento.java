package com.workshopmanagement.rdmotors.reportes.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * El descuento de una venta repartido entre sus renglones, en proporción a su valor (spec 0007, decisión 3).
 *
 * <p>Un filtro de $24.000 y unas pastillas de $12.000 con $3.600 de descuento quedan en $21.600 y $10.800. Cada
 * parte se redondea hacia abajo, y los pesos que sobran van al renglón más caro (el primero, si empatan): <b>los
 * netos suman el total de la venta al peso</b>.
 */
public final class RepartoDeDescuento {

    private RepartoDeDescuento() {
    }

    /**
     * @param totales el total de cada renglón, en su orden
     * @return el neto de cada renglón, en el mismo orden
     */
    public static List<Dinero> netos(List<Dinero> totales, Dinero descuento) {
        if (totales.isEmpty()) {
            throw new IllegalArgumentException("Una venta sin renglones no reparte descuento");
        }
        BigDecimal subtotal = totales.stream().map(Dinero::valor).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (descuento.esCero()) {
            return List.copyOf(totales);
        }
        if (subtotal.signum() <= 0 || descuento.esNegativo() || descuento.valor().compareTo(subtotal) > 0) {
            throw new IllegalArgumentException("Un descuento de " + descuento + " no cabe en " + subtotal);
        }

        List<BigDecimal> partes = new ArrayList<>();
        BigDecimal repartido = BigDecimal.ZERO;
        int masCaro = 0;
        for (int i = 0; i < totales.size(); i++) {
            BigDecimal parte = descuento.valor().multiply(totales.get(i).valor())
                    .divide(subtotal, 0, RoundingMode.FLOOR);
            partes.add(parte);
            repartido = repartido.add(parte);
            if (totales.get(i).compareTo(totales.get(masCaro)) > 0) {
                masCaro = i;
            }
        }
        partes.set(masCaro, partes.get(masCaro).add(descuento.valor().subtract(repartido)));

        List<Dinero> netos = new ArrayList<>();
        for (int i = 0; i < totales.size(); i++) {
            netos.add(Dinero.de(totales.get(i).valor().subtract(partes.get(i))));
        }
        return netos;
    }
}
