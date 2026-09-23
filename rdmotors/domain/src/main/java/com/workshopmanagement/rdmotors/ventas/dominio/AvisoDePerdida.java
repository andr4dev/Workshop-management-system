package com.workshopmanagement.rdmotors.ventas.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * Si la venta queda por debajo de lo que costaron sus repuestos al costo promedio (spec 0003, RF-017).
 *
 * <p>Hasta el spec 0004 lo calculaba la pantalla. Ahora el cajero no recibe costos, así que la cuenta la hace el
 * servidor y a él solo le dice <b>si</b> la venta queda a pérdida; el administrador ve las cifras. Es la misma
 * regla que tenía {@code utils/venta.js}, con sus mismos casos de prueba.
 *
 * <p>Solo cuentan los repuestos de costo conocido: uno sin costo no puede decir si se vende a pérdida. El
 * descuento se reparte en proporción al precio: se compara lo que se cobra <b>por los de costo conocido</b>.
 *
 * @param sinCosto cuántos renglones quedaron fuera de la cuenta por no tener costo
 */
public record AvisoDePerdida(Dinero costo, Dinero cobrado, Dinero diferencia, int sinCosto) {

    /** @param costoPromedio {@code null} si no se conoce */
    public record Renglon(Dinero precioUnitario, int cantidad, BigDecimal costoPromedio) {

        BigDecimal precio() {
            return precioUnitario.valor().multiply(BigDecimal.valueOf(cantidad));
        }
    }

    /** Vacío si no queda a pérdida, o si ningún repuesto tiene costo conocido. */
    public static Optional<AvisoDePerdida> calcular(List<Renglon> renglones, Dinero descuento) {
        List<Renglon> conCosto = renglones.stream().filter(r -> r.costoPromedio() != null).toList();
        if (conCosto.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal costo = conCosto.stream()
                .map(r -> r.costoPromedio().multiply(BigDecimal.valueOf(r.cantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal subtotal = sumar(renglones);
        BigDecimal total = subtotal.subtract(descuento == null ? BigDecimal.ZERO : descuento.valor());
        BigDecimal cobrado = subtotal.signum() == 0 ? BigDecimal.ZERO
                : total.multiply(sumar(conCosto)).divide(subtotal, 0, RoundingMode.HALF_UP);
        if (cobrado.compareTo(costo) >= 0) {
            return Optional.empty();
        }
        return Optional.of(new AvisoDePerdida(Dinero.de(costo), Dinero.de(cobrado), Dinero.de(costo.subtract(cobrado)),
                renglones.size() - conCosto.size()));
    }

    private static BigDecimal sumar(List<Renglon> renglones) {
        return renglones.stream().map(Renglon::precio).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
