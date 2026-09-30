package com.workshopmanagement.rdmotors.reportes.dominio;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.RepartoDeDescuento;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;

/**
 * Lo cobrado de cada venta, repartido en sus renglones y en su costo (spec 0014, decisiones 1 a 4).
 *
 * <p><b>Lo fiado cuenta cuando se cobra, y en la parte que se cobra.</b> Si de una venta de $55.000 entran $11.000, en
 * el reporte entra el 20 % de cada renglón y el 20 % de su costo: la utilidad de ese día es lo que entró menos lo que
 * costó eso que entró. Una venta de contado es un solo cobro, por todo, el día de la venta: queda como siempre.
 *
 * <p><b>Por acumulado.</b> Para cada cobro se reparte lo cobrado <i>hasta él</i>, y su parte es la diferencia con lo
 * repartido hasta el anterior. Así, cuando la venta se termina de pagar, sus partes suman exacto cada renglón y su
 * costo, sin pesos sueltos. Lo cobrado se reparte entre los renglones como el descuento (el residuo va al más caro):
 * las partes de un cobro suman lo cobrado al peso.
 *
 * <p><b>La venta se cuenta cuando se completa</b> (decisión 4): en el cobro que lleva lo cobrado a su total. Mientras
 * tanto su plata sí está en las ventas netas, pero la venta no.
 */
public final class LoCobrado {

    private LoCobrado() {
    }

    /**
     * Un renglón de una venta, en la parte que le toca a un cobro.
     *
     * @param neto  su parte de lo cobrado, con el descuento ya restado
     * @param costo su parte del costo; {@code null} si el renglón no tiene costo
     */
    public record Pedazo(RenglonVendido renglon, Dinero neto, Dinero costo) {

        public boolean tieneCosto() {
            return costo != null;
        }
    }

    /**
     * Lo que un cobro trae de su venta.
     *
     * @param monto    lo cobrado: lo que suma a las ventas netas
     * @param bruto    la parte de los renglones antes del descuento
     * @param deAbonos la parte de {@code monto} que vino de abonos
     * @param completa con este cobro la venta quedó pagada: ese día cuenta como venta
     * @param pedazos  uno por renglón de la venta, en su orden
     */
    public record Cobrado(VentaCobrada venta, LocalDate dia, Dinero monto, Dinero bruto, Dinero efectivo,
                          Dinero transferencia, Dinero deAbonos, boolean completa, List<Pedazo> pedazos) {

        /** La parte del descuento de la venta que le toca a este cobro. */
        public Dinero descuento() {
            return bruto.menos(monto);
        }
    }

    /**
     * Cada cobro, con lo que trae de su venta, en el orden en que entraron.
     *
     * @param ventas    las ventas de los cobros, no anuladas
     * @param renglones los de esas ventas
     * @param cobros    todos los de esas ventas hasta el final del período, también los de antes: hacen falta para el
     *                  acumulado
     * @throws IllegalArgumentException si un cobro es de una venta que no vino, una venta no tiene renglones, o lo
     *                                  cobrado pasa del total
     */
    public static List<Cobrado> de(List<VentaCobrada> ventas, List<RenglonVendido> renglones,
                                   List<CobroDeVenta> cobros) {
        Map<UUID, VentaCobrada> porId = ventas.stream()
                .collect(Collectors.toMap(VentaCobrada::id, Function.identity(), (a, b) -> a));
        Map<UUID, List<RenglonVendido>> renglonesDe = new HashMap<>();
        for (RenglonVendido renglon : renglones) {
            renglonesDe.computeIfAbsent(renglon.ventaId(), id -> new ArrayList<>()).add(renglon);
        }
        Map<UUID, List<CobroDeVenta>> cobrosDe = new LinkedHashMap<>();
        cobros.stream()
                .sorted(Comparator.comparing(CobroDeVenta::momento).thenComparing(CobroDeVenta::deAbono))
                .forEach(c -> cobrosDe.computeIfAbsent(c.ventaId(), id -> new ArrayList<>()).add(c));

        List<Cobrado> cobrados = new ArrayList<>();
        for (Map.Entry<UUID, List<CobroDeVenta>> deUnaVenta : cobrosDe.entrySet()) {
            VentaCobrada venta = porId.get(deUnaVenta.getKey());
            if (venta == null) {
                throw new IllegalArgumentException("Hay cobros de una venta que no vino: " + deUnaVenta.getKey());
            }
            List<RenglonVendido> suyos = renglonesDe.get(venta.id());
            if (suyos == null || suyos.isEmpty()) {
                throw new IllegalArgumentException("La venta " + venta.id() + " no trae sus renglones");
            }
            suyos.sort(Comparator.comparingInt(RenglonVendido::posicion));
            cobrados.addAll(deLaVenta(venta, suyos, deUnaVenta.getValue()));
        }
        cobrados.sort(Comparator.comparing(Cobrado::dia));
        return cobrados;
    }

    private static List<Cobrado> deLaVenta(VentaCobrada venta, List<RenglonVendido> renglones,
                                           List<CobroDeVenta> cobros) {
        Dinero total = venta.total();
        Dinero bruto = total.mas(venta.descuento());
        List<Dinero> netos = RepartoDeDescuento.netos(renglones.stream().map(RenglonVendido::total).toList(),
                venta.descuento());

        List<Cobrado> cobrados = new ArrayList<>();
        Dinero acumulado = Dinero.CERO;
        List<Dinero> netosAntes = ceros(renglones.size());
        List<Dinero> costosAntes = ceros(renglones.size());
        Dinero brutoAntes = Dinero.CERO;
        for (CobroDeVenta cobro : cobros) {
            acumulado = acumulado.mas(cobro.monto());
            if (acumulado.esMayorQue(total)) {
                throw new IllegalArgumentException("A la venta " + venta.id() + " le entraron " + acumulado
                        + " y vale " + total);
            }
            List<Dinero> netosHasta = total.esCero() ? ceros(renglones.size())
                    : RepartoDeDescuento.netos(netos, total.menos(acumulado));
            Dinero brutoHasta = parte(bruto, acumulado, total);

            List<Pedazo> pedazos = new ArrayList<>();
            List<Dinero> costosHasta = new ArrayList<>();
            for (int i = 0; i < renglones.size(); i++) {
                RenglonVendido renglon = renglones.get(i);
                Dinero costoHasta = renglon.tieneCosto() ? parte(renglon.costo(), acumulado, total) : Dinero.CERO;
                costosHasta.add(costoHasta);
                pedazos.add(new Pedazo(renglon, netosHasta.get(i).menos(netosAntes.get(i)),
                        renglon.tieneCosto() ? costoHasta.menos(costosAntes.get(i)) : null));
            }

            Dinero monto = cobro.monto();
            cobrados.add(new Cobrado(venta, cobro.dia(), monto, brutoHasta.menos(brutoAntes),
                    cobro.forma() == FormaPago.EFECTIVO ? monto : Dinero.CERO,
                    cobro.forma() == FormaPago.TRANSFERENCIA ? monto : Dinero.CERO,
                    cobro.deAbono() ? monto : Dinero.CERO,
                    acumulado.equals(total) && !cobrosCompletos(cobrados),
                    List.copyOf(pedazos)));
            netosAntes = netosHasta;
            costosAntes = costosHasta;
            brutoAntes = brutoHasta;
        }
        return cobrados;
    }

    /** Si alguno de los cobros anteriores ya completó la venta: una venta se cuenta una sola vez. */
    private static boolean cobrosCompletos(List<Cobrado> anteriores) {
        return anteriores.stream().anyMatch(Cobrado::completa);
    }

    /**
     * La parte de {@code monto} que corresponde a {@code cobrado} de {@code total}, redondeada al peso. Una venta de $0
     * (regalada con el descuento) se completa en su único cobro: ahí va todo, también su costo.
     */
    private static Dinero parte(Dinero monto, Dinero cobrado, Dinero total) {
        if (total.esCero()) {
            return monto;
        }
        return Dinero.de(monto.valor().multiply(cobrado.valor()).divide(total.valor(), 0, RoundingMode.HALF_UP));
    }

    private static List<Dinero> ceros(int cuantos) {
        List<Dinero> ceros = new ArrayList<>();
        for (int i = 0; i < cuantos; i++) {
            ceros.add(Dinero.CERO);
        }
        return ceros;
    }
}
