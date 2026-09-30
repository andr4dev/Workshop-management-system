package com.workshopmanagement.rdmotors.clientes.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Cuánto queda debiendo de cada producto (spec 0016, decisión 2): el descuento en proporción, y lo pagado al llevárselo
 * cubre lo que el cajero marcó; sin marcar, en el orden de la venta.
 */
class RepartoDelFiadoTest {

    private static final Dinero MOTUL = Dinero.de(65_000);
    private static final Dinero FILTRO = Dinero.de(11_000);

    private static List<Long> fiados(long descuento, long fiado, Integer... marcados) {
        return RepartoDelFiado.porRenglon(List.of(MOTUL, FILTRO), Dinero.de(descuento), Dinero.de(fiado),
                List.of(marcados)).stream().map(d -> d.valor().longValueExact()).toList();
    }

    @Test
    @DisplayName("sin pagar nada al llevárselo: cada producto debe su precio exacto")
    void todoFiado() {
        assertThat(fiados(0, 76_000)).containsExactly(65_000L, 11_000L);
    }

    @Test
    @DisplayName("paga el filtro, marcado: el MOTUL debe sus $65.000 y el filtro queda en $0")
    void pagaLoMarcado() {
        assertThat(fiados(0, 65_000, 1)).containsExactly(65_000L, 0L);
    }

    @Test
    @DisplayName("paga $20.000 sin marcar: cubre en el orden de la venta, no en proporción")
    void sinMarcarEnOrden() {
        assertThat(fiados(0, 56_000)).containsExactly(45_000L, 11_000L);
    }

    @Test
    @DisplayName("marca el filtro y paga más: lo que sobra sigue al MOTUL; paga menos: el filtro queda a medias")
    void masYMenosDeLoMarcado() {
        assertThat(fiados(0, 56_000, 1)).as("paga $20.000").containsExactly(56_000L, 0L);
        assertThat(fiados(0, 71_000, 1)).as("paga $5.000").containsExactly(65_000L, 6_000L);
    }

    @Test
    @DisplayName("con descuento: lo que vale cada uno baja en proporción (el peso de más al más caro) y suman lo fiado")
    void conDescuento() {
        // $3.000 de descuento sobre $76.000: el MOTUL baja 2.566 (2.565 + el peso que sobra), el filtro 434.
        assertThat(fiados(3_000, 73_000)).containsExactly(62_434L, 10_566L);
        List<Long> conPago = fiados(3_000, 60_000, 1);
        assertThat(conPago).as("pagó el filtro y $2.434 más").containsExactly(60_000L, 0L);
        assertThat(conPago.stream().mapToLong(Long::longValue).sum()).isEqualTo(60_000L);
    }

    @Test
    @DisplayName("las partes suman lo fiado, al peso, en cualquier combinación")
    void sumanLoFiado() {
        for (long descuento : new long[] {0, 1, 999, 3_000, 7_777}) {
            for (long fiado : new long[] {1, 10_999, 40_000, 76_000 - descuento}) {
                for (Integer[] marcados : new Integer[][] {{}, {1}, {0}, {1, 0}}) {
                    long suma = fiados(descuento, fiado, marcados).stream().mapToLong(Long::longValue).sum();
                    assertThat(suma).as("descuento %d, fiado %d", descuento, fiado).isEqualTo(fiado);
                }
            }
        }
    }

    @Test
    @DisplayName("lo fiado no puede pasar de la venta, y lo marcado tiene que ser de ella")
    void loQueNoCabe() {
        assertThatThrownBy(() -> fiados(0, 76_001)).isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("no cabe");
        assertThatThrownBy(() -> fiados(0, 60_000, 2)).hasMessage("Lo que se paga ahora tiene que ser de esta venta");
    }
}
