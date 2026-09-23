package com.workshopmanagement.rdmotors.reportes.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/** Un gasto del mes repartido día por día (spec 0007, RF-010a). */
class RepartoDelMesTest {

    private static List<Dinero> cuotas(long monto, YearMonth mes) {
        List<Dinero> cuotas = new ArrayList<>();
        for (int dia = 1; dia <= mes.lengthOfMonth(); dia++) {
            cuotas.add(RepartoDelMes.cuota(Dinero.de(monto), mes.atDay(dia)));
        }
        return cuotas;
    }

    @Test
    @DisplayName("$800.000 en septiembre: 20 días de $26.667 y 10 de $26.666, que suman el arriendo al peso")
    void septiembre() {
        List<Dinero> cuotas = cuotas(800_000, YearMonth.of(2026, 9));

        assertThat(cuotas.subList(0, 20)).containsOnly(Dinero.de(26_667));
        assertThat(cuotas.subList(20, 30)).containsOnly(Dinero.de(26_666));
        assertThat(DatosDeReporte.suma(cuotas, c -> c)).isEqualTo(Dinero.de(800_000));
    }

    @Test
    @DisplayName("octubre: el 1 carga $25.807; la semana del 14 al 20 de septiembre carga 7 cuotas")
    void cuotaDelDiaYDeUnaSemana() {
        assertThat(RepartoDelMes.cuota(Dinero.de(800_000), LocalDate.of(2026, 10, 1))).isEqualTo(Dinero.de(25_807));
        assertThat(DatosDeReporte.suma(cuotas(800_000, YearMonth.of(2026, 10)), c -> c)).isEqualTo(Dinero.de(800_000));

        Periodo semana = new Periodo(LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 20));
        assertThat(RepartoDelMes.enElPeriodo(Dinero.de(800_000), YearMonth.of(2026, 9), semana))
                .isEqualTo(Dinero.de(7 * 26_667));
        assertThat(RepartoDelMes.enElPeriodo(Dinero.de(800_000), YearMonth.of(2026, 10), semana)).isEqualTo(Dinero.CERO);
    }

    @Test
    @DisplayName("cualquier monto y cualquier mes suman exacto, y ninguna cuota difiere de otra en más de un peso")
    void siempreSumaExacto() {
        for (long monto : new long[] {1, 17, 29, 3_000_000, 1_234_567, 999_999_999}) {
            for (YearMonth mes : List.of(YearMonth.of(2028, 2), YearMonth.of(2026, 2), YearMonth.of(2026, 4),
                    YearMonth.of(2026, 12))) {
                List<Dinero> cuotas = cuotas(monto, mes);
                assertThat(DatosDeReporte.suma(cuotas, c -> c)).as(monto + " en " + mes).isEqualTo(Dinero.de(monto));
                assertThat(cuotas).as(monto + " en " + mes)
                        .allMatch(c -> !c.menos(cuotas.getLast()).esMayorQue(Dinero.de(1)) && !c.esNegativo());
            }
        }
    }
}
