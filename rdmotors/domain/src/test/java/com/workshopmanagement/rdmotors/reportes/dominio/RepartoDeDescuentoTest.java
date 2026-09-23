package com.workshopmanagement.rdmotors.reportes.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/** El descuento de una venta repartido entre sus renglones (spec 0007, decisión 3). */
class RepartoDeDescuentoTest {

    private static List<Dinero> pesos(long... montos) {
        return Arrays.stream(montos).mapToObj(Dinero::de).toList();
    }

    @Test
    @DisplayName("un filtro de $24.000 y unas pastillas de $12.000 con $3.600 de descuento: $21.600 y $10.800")
    void ejemploDelSpec() {
        assertThat(RepartoDeDescuento.netos(pesos(24_000, 12_000), Dinero.de(3_600)))
                .containsExactly(Dinero.de(21_600), Dinero.de(10_800));
    }

    @Test
    @DisplayName("tres de $10.000 con $1.000: el peso que sobra va al más caro (el primero, si empatan) y suman exacto")
    void elRestoAlMasCaro() {
        assertThat(RepartoDeDescuento.netos(pesos(10_000, 10_000, 10_000), Dinero.de(1_000)))
                .containsExactly(Dinero.de(9_666), Dinero.de(9_667), Dinero.de(9_667));
        // 263,1 + 368,4 + 368,4 → 263 + 368 + 368 = 999: el peso que falta, al de $7.001.
        assertThat(RepartoDeDescuento.netos(pesos(5_000, 7_000, 7_001), Dinero.de(1_000)))
                .containsExactly(Dinero.de(4_737), Dinero.de(6_632), Dinero.de(6_632));
    }

    @Test
    @DisplayName("sin descuento quedan igual; con el 100 %, en $0; y siempre suman el total de la venta")
    void sumanElTotal() {
        assertThat(RepartoDeDescuento.netos(pesos(24_000, 12_000), Dinero.CERO))
                .containsExactly(Dinero.de(24_000), Dinero.de(12_000));
        assertThat(RepartoDeDescuento.netos(pesos(24_000, 12_000), Dinero.de(36_000))).containsOnly(Dinero.CERO);

        List<List<Dinero>> ventas = List.of(pesos(1_000), pesos(3_333, 6_667), pesos(12_500, 8_300, 450, 99_990),
                pesos(7, 11, 13));
        for (List<Dinero> renglones : ventas) {
            Dinero subtotal = DatosDeReporte.suma(renglones, d -> d);
            for (long descuento : new long[] {1, 3, 17, 31, 999}) {
                if (descuento > subtotal.valor().longValue()) {
                    continue;
                }
                List<Dinero> netos = RepartoDeDescuento.netos(renglones, Dinero.de(descuento));
                assertThat(DatosDeReporte.suma(netos, d -> d)).as(renglones + " − " + descuento)
                        .isEqualTo(subtotal.menos(Dinero.de(descuento)));
            }
        }
    }

    @Test
    @DisplayName("un descuento mayor que la venta, o una venta sin renglones, no se reparte")
    void imposibles() {
        assertThatThrownBy(() -> RepartoDeDescuento.netos(pesos(1_000), Dinero.de(1_001)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RepartoDeDescuento.netos(List.of(), Dinero.de(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
