package com.workshopmanagement.rdmotors.reportes.dominio;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * El período de un reporte: del primer al último día, los dos incluidos, en días de Colombia (spec 0007, RF-001 a
 * RF-003).
 *
 * <p>Los días se cortan en hora de Colombia: una venta cobrada a las 7:30 p. m. es de ese día aunque en hora
 * universal ya sea el siguiente. Por eso el período se busca por {@link #inicio()} y {@link #fin()}, que son las
 * medianoches de Colombia.
 */
public record Periodo(LocalDate desde, LocalDate hasta) {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");
    public static final int MAXIMO_DE_DIAS = 366;
    /** Hasta este largo, el día por día va por días; más largo, por semanas (RF-018). */
    public static final int MAXIMO_DE_DIAS_POR_DIA = 62;

    public Periodo {
        if (desde == null || hasta == null) {
            throw new ReglaDeNegocioException("Elige la fecha inicial y la final del período");
        }
        if (desde.isAfter(hasta)) {
            throw new ReglaDeNegocioException("La fecha inicial es posterior a la final");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAXIMO_DE_DIAS) {
            throw new ReglaDeNegocioException("Un período no puede pasar de " + MAXIMO_DE_DIAS + " días");
        }
    }

    /** El período que se pide en un reporte: además, no termina después de hoy. */
    public static Periodo pedido(LocalDate desde, LocalDate hasta, LocalDate hoy) {
        Periodo periodo = new Periodo(desde, hasta);
        if (hasta.isAfter(hoy)) {
            throw new ReglaDeNegocioException("El período no puede terminar después de hoy");
        }
        return periodo;
    }

    public int dias() {
        return (int) ChronoUnit.DAYS.between(desde, hasta) + 1;
    }

    public boolean contiene(LocalDate dia) {
        return !dia.isBefore(desde) && !dia.isAfter(hasta);
    }

    /** La medianoche de Colombia con que empieza el primer día. */
    public Instant inicio() {
        return desde.atStartOfDay(ZONA).toInstant();
    }

    /** La medianoche de Colombia con que termina el último día; no se incluye. */
    public Instant fin() {
        return hasta.plusDays(1).atStartOfDay(ZONA).toInstant();
    }

    public Agrupacion agrupacion() {
        return dias() <= MAXIMO_DE_DIAS_POR_DIA ? Agrupacion.DIA : Agrupacion.SEMANA;
    }

    /** Los tramos del día por día: cada día, o cada semana de lunes a domingo recortada al período. */
    public List<Periodo> tramos() {
        List<Periodo> tramos = new ArrayList<>();
        LocalDate inicio = desde;
        while (!inicio.isAfter(hasta)) {
            LocalDate fin = agrupacion() == Agrupacion.DIA
                    ? inicio
                    : min(inicio.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)), hasta);
            tramos.add(new Periodo(inicio, fin));
            inicio = fin.plusDays(1);
        }
        return tramos;
    }

    /**
     * Con qué se compara (RF-022):
     * <ul>
     *   <li>un mes completo, con el mes anterior completo;</li>
     *   <li>del 1 a un día del mismo mes, con el mes anterior del 1 al mismo día (recortado a su último día);</li>
     *   <li>de un lunes a un día de la misma semana, con la semana anterior hasta el mismo día;</li>
     *   <li>cualquier otro, y un solo día, con los mismos días justo antes.</li>
     * </ul>
     */
    public Periodo anterior() {
        YearMonth mes = YearMonth.from(desde);
        if (dias() > 1 && desde.getDayOfMonth() == 1 && YearMonth.from(hasta).equals(mes)) {
            YearMonth previo = mes.minusMonths(1);
            return hasta.equals(mes.atEndOfMonth())
                    ? new Periodo(previo.atDay(1), previo.atEndOfMonth())
                    : new Periodo(previo.atDay(1), previo.atDay(Math.min(hasta.getDayOfMonth(), previo.lengthOfMonth())));
        }
        if (dias() > 1 && dias() <= 7 && desde.getDayOfWeek() == DayOfWeek.MONDAY) {
            return new Periodo(desde.minusWeeks(1), hasta.minusWeeks(1));
        }
        return new Periodo(desde.minusDays(dias()), hasta.minusDays(dias()));
    }

    /** Si el período toca algún día del mes. */
    public boolean tocaElMes(YearMonth mes) {
        return !desde.isAfter(mes.atEndOfMonth()) && !hasta.isBefore(mes.atDay(1));
    }

    /** Si el período cubre el mes del 1 al último día, o del 1 a hoy en el mes en curso (RF-010a). */
    public boolean cubreElMes(YearMonth mes, LocalDate hoy) {
        return !desde.isAfter(mes.atDay(1)) && !hasta.isBefore(min(mes.atEndOfMonth(), hoy));
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
