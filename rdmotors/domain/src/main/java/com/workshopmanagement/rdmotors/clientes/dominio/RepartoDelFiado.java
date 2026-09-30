package com.workshopmanagement.rdmotors.clientes.dominio;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.RepartoDeDescuento;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Cuánto queda debiendo el cliente de cada producto de una venta fiada (spec 0016, decisión 2).
 *
 * <ol>
 *   <li><b>Lo que vale cada producto</b>: su total menos su parte del descuento, repartido en proporción (la regla del
 *       comprobante y de los reportes). Suman el total de la venta.</li>
 *   <li><b>Lo que pagó al llevárselo</b> ({@code total − fiado}) cubre primero los productos que <b>el cajero marcó</b>,
 *       en ese orden, y si sobra, los demás en el orden de la venta. Sin marcar nada, en el orden de la venta: así las
 *       cifras quedan redondas (MOTUL $65.000 + filtro $11.000, paga el filtro → el MOTUL debe $65.000).</li>
 *   <li>Lo que no alcanzó a cubrir es lo fiado de cada uno. <b>Suman lo fiado, al peso.</b></li>
 * </ol>
 */
public final class RepartoDelFiado {

    private RepartoDelFiado() {
    }

    /**
     * @param totales     el total de cada renglón antes del descuento, en el orden de la venta
     * @param descuento   el de la venta; {@link Dinero#CERO} si no hubo
     * @param fiado       lo que quedó debiendo de toda la venta
     * @param pagaPrimero las posiciones (desde 0) de los renglones que el cajero marcó como pagados, en su orden
     * @return lo fiado de cada renglón, en el orden de la venta; $0 en los que quedaron pagos
     */
    public static List<Dinero> porRenglon(List<Dinero> totales, Dinero descuento, Dinero fiado,
                                          List<Integer> pagaPrimero) {
        List<Dinero> netos = RepartoDeDescuento.netos(totales, descuento);
        Dinero total = netos.stream().reduce(Dinero.CERO, Dinero::mas);
        if (fiado.esNegativo() || fiado.esMayorQue(total)) {
            throw new ReglaDeNegocioException("Lo fiado (" + fiado.enPesos() + ") no cabe en la venta de "
                    + total.enPesos());
        }
        Set<Integer> orden = new LinkedHashSet<>();
        for (Integer i : pagaPrimero) {
            if (i == null || i < 0 || i >= netos.size()) {
                throw new ReglaDeNegocioException("Lo que se paga ahora tiene que ser de esta venta");
            }
            orden.add(i);
        }
        for (int i = 0; i < netos.size(); i++) {
            orden.add(i);
        }

        List<Dinero> fiados = new ArrayList<>(netos);
        Dinero pagado = total.menos(fiado);
        for (int i : orden) {
            if (pagado.esCero()) {
                break;
            }
            Dinero cubre = pagado.esMayorQue(netos.get(i)) ? netos.get(i) : pagado;
            fiados.set(i, netos.get(i).menos(cubre));
            pagado = pagado.menos(cubre);
        }
        return List.copyOf(fiados);
    }
}
