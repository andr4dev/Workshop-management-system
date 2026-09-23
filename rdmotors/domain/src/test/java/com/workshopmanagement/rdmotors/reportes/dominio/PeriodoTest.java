package com.workshopmanagement.rdmotors.reportes.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/** El período de un reporte (spec 0007, RF-001 a RF-003, RF-018 y RF-022). */
class PeriodoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 17);

    private static Periodo del(String desde, String hasta) {
        return new Periodo(LocalDate.parse(desde), LocalDate.parse(hasta));
    }

    @Test
    @DisplayName("al revés, de más de 366 días o hasta mañana no se consulta, y dice por qué")
    void validaciones() {
        assertThatThrownBy(() -> del("2026-09-20", "2026-09-14"))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessage("La fecha inicial es posterior a la final");
        assertThatThrownBy(() -> del("2025-09-16", "2026-09-17"))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessage("Un período no puede pasar de 366 días");
        assertThatThrownBy(() -> Periodo.pedido(HOY, HOY.plusDays(1), HOY))
                .isInstanceOf(ReglaDeNegocioException.class).hasMessage("El período no puede terminar después de hoy");
        assertThatThrownBy(() -> Periodo.pedido(null, HOY, HOY)).isInstanceOf(ReglaDeNegocioException.class);

        assertThat(del("2025-09-17", "2026-09-17").dias()).isEqualTo(366);
        assertThat(Periodo.pedido(HOY, HOY, HOY).dias()).isEqualTo(1);
    }

    @Test
    @DisplayName("RF-002: los días se cortan a medianoche de Colombia (5:00 a. m. en hora universal)")
    void medianocheDeColombia() {
        Periodo semana = del("2026-09-14", "2026-09-20");

        assertThat(semana.inicio()).isEqualTo(Instant.parse("2026-09-14T05:00:00Z"));
        assertThat(semana.fin()).isEqualTo(Instant.parse("2026-09-21T05:00:00Z"));
    }

    @Test
    @DisplayName("RF-018: hasta 62 días va por días; 63 ya va por semanas de lunes a domingo, recortadas al período")
    void agrupacionYTramos() {
        Periodo sesentaYDos = del("2026-07-01", "2026-08-31");
        Periodo sesentaYTres = del("2026-07-01", "2026-09-01");

        assertThat(sesentaYDos.dias()).isEqualTo(62);
        assertThat(sesentaYDos.agrupacion()).isEqualTo(Agrupacion.DIA);
        assertThat(sesentaYDos.tramos()).hasSize(62).allMatch(t -> t.desde().equals(t.hasta()));

        assertThat(sesentaYTres.agrupacion()).isEqualTo(Agrupacion.SEMANA);
        // El 1 de julio de 2026 es miércoles; el 1 de septiembre, martes.
        assertThat(sesentaYTres.tramos().getFirst()).isEqualTo(del("2026-07-01", "2026-07-05"));
        assertThat(sesentaYTres.tramos().get(1)).isEqualTo(del("2026-07-06", "2026-07-12"));
        assertThat(sesentaYTres.tramos().getLast()).isEqualTo(del("2026-08-31", "2026-09-01"));
        assertThat(sesentaYTres.tramos().stream().mapToInt(Periodo::dias).sum()).isEqualTo(63);
    }

    @Test
    @DisplayName("RF-022: la semana contra la anterior, esta semana y este mes hasta el mismo día, el mes completo contra el anterior completo")
    void anterior() {
        assertThat(del("2026-09-14", "2026-09-20").anterior()).as("semana pasada").isEqualTo(del("2026-09-07", "2026-09-13"));
        assertThat(del("2026-09-14", "2026-09-17").anterior()).as("esta semana").isEqualTo(del("2026-09-07", "2026-09-10"));
        assertThat(del("2026-09-01", "2026-09-17").anterior()).as("este mes").isEqualTo(del("2026-08-01", "2026-08-17"));
        assertThat(del("2026-08-01", "2026-08-31").anterior()).as("mes pasado").isEqualTo(del("2026-07-01", "2026-07-31"));
        assertThat(del("2026-03-01", "2026-03-31").anterior()).as("marzo completo").isEqualTo(del("2026-02-01", "2026-02-28"));
        assertThat(del("2026-03-01", "2026-03-30").anterior()).as("marzo al 30").isEqualTo(del("2026-02-01", "2026-02-28"));
        assertThat(del("2026-09-03", "2026-09-12").anterior()).as("rango de 10 días").isEqualTo(del("2026-08-24", "2026-09-02"));
        assertThat(del("2026-09-01", "2026-09-01").anterior()).as("un día, el 1").isEqualTo(del("2026-08-31", "2026-08-31"));
        assertThat(del("2026-09-14", "2026-09-14").anterior()).as("un lunes").isEqualTo(del("2026-09-13", "2026-09-13"));
    }

    @Test
    @DisplayName("RF-010a: cubrir el mes es del 1 al último día, o del 1 a hoy en el mes en curso")
    void cubreElMes() {
        YearMonth septiembre = YearMonth.of(2026, 9);

        assertThat(del("2026-09-01", "2026-09-17").cubreElMes(septiembre, HOY)).as("mes en curso a hoy").isTrue();
        assertThat(del("2026-09-01", "2026-09-16").cubreElMes(septiembre, HOY)).as("hasta ayer").isFalse();
        assertThat(del("2026-09-02", "2026-09-17").cubreElMes(septiembre, HOY)).as("sin el 1").isFalse();
        assertThat(del("2026-08-01", "2026-08-31").cubreElMes(YearMonth.of(2026, 8), HOY)).isTrue();
        assertThat(del("2026-07-15", "2026-09-17").cubreElMes(YearMonth.of(2026, 8), HOY)).as("un rango que lo contiene").isTrue();
        assertThat(del("2026-08-01", "2026-08-30").cubreElMes(YearMonth.of(2026, 8), HOY)).isFalse();

        assertThat(del("2026-08-31", "2026-09-01").tocaElMes(septiembre)).isTrue();
        assertThat(del("2026-08-01", "2026-08-31").tocaElMes(septiembre)).isFalse();
    }
}
