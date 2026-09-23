package com.workshopmanagement.rdmotors.reportes.dominio;

import java.time.LocalDate;
import java.time.YearMonth;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * Un gasto del mes repartido día por día (spec 0007, RF-010a).
 *
 * <p>Cada día del mes carga el monto entre los días del mes, en pesos enteros, y los pesos que sobran van a los
 * primeros días: $800.000 en septiembre son 20 días de $26.667 y 10 de $26.666. <b>Las cuotas suman el monto al
 * peso</b>: el mes completo carga el gasto entero, ni un peso más ni uno menos.
 */
public final class RepartoDelMes {

    private RepartoDelMes() {
    }

    /** La cuota que carga un día de su mes. */
    public static Dinero cuota(Dinero monto, LocalDate dia) {
        int dias = YearMonth.from(dia).lengthOfMonth();
        long pesos = monto.valor().longValueExact();
        long cuota = Math.floorDiv(pesos, dias);
        long sobran = pesos - cuota * dias;
        return Dinero.de(dia.getDayOfMonth() <= sobran ? cuota + 1 : cuota);
    }

    /** Lo que carga un período: las cuotas de sus días que caen en el mes. */
    public static Dinero enElPeriodo(Dinero monto, YearMonth mes, Periodo periodo) {
        Dinero total = Dinero.CERO;
        for (LocalDate dia = mes.atDay(1); !dia.isAfter(mes.atEndOfMonth()); dia = dia.plusDays(1)) {
            if (periodo.contiene(dia)) {
                total = total.mas(cuota(monto, dia));
            }
        }
        return total;
    }
}
