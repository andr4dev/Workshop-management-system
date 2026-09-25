package com.workshopmanagement.rdmotors.carga.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/** Las partes suman el total (spec 0012, RF-004a). */
class RepartoDelIvaTest {

    private static List<Dinero> pesos(long... valores) {
        List<Dinero> lista = new ArrayList<>();
        for (long v : valores) {
            lista.add(Dinero.de(v));
        }
        return lista;
    }

    private static long suma(List<Dinero> lista) {
        return lista.stream().mapToLong(d -> d.valor().longValueExact()).sum();
    }

    @Test
    @DisplayName("cuando el 19% da exacto, cada renglón lleva su 19%")
    void exacto() {
        assertThat(RepartoDelIva.conIva(pesos(100, 200, 300), Dinero.de(114))).containsExactlyElementsOf(pesos(119, 238, 357));
    }

    @Test
    @DisplayName("LA SUMA ES EXACTAMENTE LO PAGADO, aunque ninguna parte dé exacta")
    void sumaExacta() {
        List<Dinero> conIva = RepartoDelIva.conIva(pesos(1, 1, 1), Dinero.de(1));

        assertThat(suma(conIva)).isEqualTo(4);
        assertThat(conIva).as("a igual resto, el peso le toca al primero: el resultado no cambia al recalcular")
                .containsExactlyElementsOf(pesos(2, 1, 1));
    }

    @Test
    @DisplayName("NINGÚN RENGLÓN CARGA EL SOBRANTE DE LOS DEMÁS: todos quedan a menos de un peso de su parte exacta")
    void restosMayores() {
        // 500 renglones de $7 y uno de $1.000. Cada renglón chico pierde $0,33 al redondear: 165 pesos en total.
        // Con "el sobrante al más caro", el de $1.000 se llevaría esos 165 pesos. Con restos mayores, van de a uno.
        List<Dinero> totales = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            totales.add(Dinero.de(7));
        }
        totales.add(Dinero.de(1_000));
        Dinero iva = Dinero.de(BigDecimal.valueOf((500 * 7 + 1_000) * 19).divide(BigDecimal.valueOf(100), 0,
                RoundingMode.HALF_UP));

        List<Dinero> conIva = RepartoDelIva.conIva(totales, iva);

        assertThat(conIva.getLast()).as("el de $1.000 lleva sus $190, no $355").isEqualTo(Dinero.de(1_190));
        assertThat(suma(conIva)).isEqualTo(500 * 7 + 1_000 + iva.valor().longValueExact());
    }

    @Test
    @DisplayName("con cualquier factura al azar, la suma cuadra al peso y ninguna parte se aleja un peso de la exacta")
    void propiedad() {
        Random azar = new Random(20260925);
        for (int factura = 0; factura < 200; factura++) {
            int renglones = 1 + azar.nextInt(700);
            List<Dinero> totales = new ArrayList<>();
            long subtotal = 0;
            for (int i = 0; i < renglones; i++) {
                long valor = 1 + azar.nextInt(400_000);
                totales.add(Dinero.de(valor));
                subtotal += valor;
            }
            long iva = BigDecimal.valueOf(subtotal).multiply(new BigDecimal("0.19"))
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();

            List<Dinero> conIva = RepartoDelIva.conIva(totales, Dinero.de(iva));

            assertThat(suma(conIva)).as("factura %d", factura).isEqualTo(subtotal + iva);
            for (int i = 0; i < renglones; i++) {
                BigDecimal exacta = BigDecimal.valueOf(iva).multiply(totales.get(i).valor())
                        .divide(BigDecimal.valueOf(subtotal), 6, RoundingMode.HALF_UP);
                BigDecimal parte = conIva.get(i).valor().subtract(totales.get(i).valor());
                assertThat(parte.subtract(exacta).abs()).as("factura %d, renglón %d", factura, i)
                        .isLessThan(BigDecimal.ONE);
            }
        }
    }

    @Test
    @DisplayName("sin IVA, o sin renglones, no hay nada que repartir")
    void nadaQueRepartir() {
        assertThat(RepartoDelIva.conIva(pesos(100, 200), Dinero.de(0))).containsExactlyElementsOf(pesos(100, 200));
        assertThat(RepartoDelIva.conIva(List.of(), Dinero.de(50))).isEmpty();
    }
}
