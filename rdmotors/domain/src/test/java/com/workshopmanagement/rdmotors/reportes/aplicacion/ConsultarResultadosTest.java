package com.workshopmanagement.rdmotors.reportes.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos.RelojFijo;
import com.workshopmanagement.rdmotors.compartido.Falsos.ReportesEnMemoria;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.NoPermitidoException;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.reportes.dominio.CarteraDelPeriodo;
import com.workshopmanagement.rdmotors.reportes.dominio.DatosDeReporte;
import com.workshopmanagement.rdmotors.reportes.dominio.Control;
import com.workshopmanagement.rdmotors.reportes.dominio.ModoGastosDelMes;
import com.workshopmanagement.rdmotors.reportes.dominio.Periodo;
import com.workshopmanagement.rdmotors.reportes.dominio.ResultadosDelPeriodo;

/** Pedir los resultados de un período (spec 0007, H1 y RF-001 a RF-003). */
class ConsultarResultadosTest {

    private final Actor admin = ActoresDePrueba.administrador();

    // Lunes 21 de septiembre, 10:00 a. m. en Colombia.
    private final RelojFijo reloj = new RelojFijo("2026-09-21T15:00:00Z");
    private final ReportesEnMemoria reportes = new ReportesEnMemoria();
    private final ConsultarResultados consultar = new ConsultarResultados(reportes, reloj);

    private void sembrar(DatosDeReporte datos) {
        reportes.ventas.addAll(datos.ventas);
        reportes.renglones.addAll(datos.renglones);
        reportes.gastos.addAll(datos.gastos);
    }

    @Test
    @DisplayName("la semana pasada lee ventas y renglones con el mismo intervalo, de medianoche a medianoche de Colombia, y da las cifras del ejemplo")
    void semanaPasada() {
        sembrar(DatosDeReporte.semanaDelEjemplo());

        ResultadosDelPeriodo r = consultar.ejecutar(DatosDeReporte.LUNES_14, DatosDeReporte.DOMINGO_20,
                ModoGastosDelMes.REPARTIDOS, admin).resultados();

        List<Instant> semana = List.of(Instant.parse("2026-09-14T05:00:00Z"), Instant.parse("2026-09-21T05:00:00Z"));
        List<Instant> semanaAnterior = List.of(Instant.parse("2026-09-07T05:00:00Z"), Instant.parse("2026-09-14T05:00:00Z"));
        assertThat(reportes.intervalosPedidos).containsExactly(semana, semana, semanaAnterior, semanaAnterior);
        assertThat(r.cifras().ventasNetas()).isEqualTo(Dinero.de(1_220_000));
        assertThat(r.cifras().utilidadOperativa()).isEqualTo(Dinero.de(320_000));
        DatosDeReporte.lasFilasSuman(r);
    }

    @Test
    @DisplayName("sin decir cómo van los gastos del mes, van repartidos; hoy es el día del reloj")
    void porDefectoRepartidos() {
        DatosDeReporte datos = new DatosDeReporte();
        datos.gastoDelMes(LocalDate.of(2026, 9, 1), "Arriendo", 800_000);
        sembrar(datos);

        ResultadosDelPeriodo r = consultar.ejecutar(LocalDate.of(2026, 9, 1), reloj.hoy(), null, admin).resultados();

        assertThat(r.modo()).isEqualTo(ModoGastosDelMes.REPARTIDOS);
        assertThat(r.cifras().gastos()).isEqualTo(Dinero.de(20 * 26_667 + 26_666));
        assertThat(consultar.ejecutar(LocalDate.of(2026, 9, 1), reloj.hoy(), ModoGastosDelMes.SOLO_EN_EL_MES, admin)
                .resultados().cifras().gastos()).as("del 1 a hoy cubre el mes en curso").isEqualTo(Dinero.de(800_000));
    }

    @Test
    @DisplayName("RF-022: este mes (del 1 al 21 de septiembre) se compara con agosto del 1 al 21, con el mismo cálculo")
    void anteriorDeEsteMes() {
        DatosDeReporte datos = new DatosDeReporte();
        datos.venta(LocalDate.of(2026, 9, 3), 0, 50_000, 0, DatosDeReporte.conCosto("FILTRO", 2, 50_000, 30_000));
        datos.venta(LocalDate.of(2026, 8, 21), 0, 40_000, 0, DatosDeReporte.conCosto("FILTRO", 2, 40_000, 30_000));
        datos.venta(LocalDate.of(2026, 8, 22), 0, 99_000, 0, DatosDeReporte.conCosto("FILTRO", 5, 99_000, 75_000));
        sembrar(datos);

        ReporteDeResultados reporte = consultar.ejecutar(LocalDate.of(2026, 9, 1), reloj.hoy(), ModoGastosDelMes.REPARTIDOS, admin);

        assertThat(reporte.periodoAnterior()).isEqualTo(new Periodo(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 21)));
        assertThat(reporte.anterior().ventasNetas()).as("el 22 de agosto ya no").isEqualTo(Dinero.de(40_000));
        assertThat(reporte.anterior().utilidadBruta()).isEqualTo(Dinero.de(10_000));
        assertThat(reporte.resultados().cifras().ventasNetas()).isEqualTo(Dinero.de(50_000));
    }

    @Test
    @DisplayName("RF-023: el control trae las anuladas del período y los turnos cerrados en él, con faltantes y sobrantes")
    void control() {
        Periodo semana = new Periodo(DatosDeReporte.LUNES_14, DatosDeReporte.DOMINGO_20);
        reportes.anuladas.put(semana.inicio(), new Control.VentasAnuladas(2, Dinero.de(45_000)));
        reportes.turnos.add(turno("2026-09-15T23:00:00Z", -1_400));
        reportes.turnos.add(turno("2026-09-16T23:00:00Z", 500));
        reportes.turnos.add(turno("2026-09-13T23:00:00Z", -9_999));   // domingo 13, 6 p. m.: la semana anterior

        Control control = consultar.ejecutar(semana.desde(), semana.hasta(), null, admin).control();

        assertThat(control.ventasAnuladas()).isEqualTo(2);
        assertThat(control.montoAnuladas()).isEqualTo(Dinero.de(45_000));
        assertThat(control.turnos()).hasSize(2);
        assertThat(control.faltantes()).isEqualTo(Dinero.de(1_400));
        assertThat(control.sobrantes()).isEqualTo(Dinero.de(500));
    }

    private static Control.TurnoCerrado turno(String cerradoEn, long diferencia) {
        Instant cierre = Instant.parse(cerradoEn);
        return new Control.TurnoCerrado(UUID.randomUUID(), cierre.minusSeconds(36_000), cierre, Dinero.de(100_000),
                Dinero.de(100_000 + diferencia), Dinero.de(diferencia));
    }

    @Test
    @DisplayName("un período hasta mañana no se consulta: dice por qué y no lee nada")
    void hastaManana() {
        assertThatThrownBy(() -> consultar.ejecutar(reloj.hoy(), reloj.hoy().plusDays(1), ModoGastosDelMes.REPARTIDOS, admin))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage("El período no puede terminar después de hoy");
        assertThat(reportes.intervalosPedidos).isEmpty();
    }

    @Test
    @DisplayName("el reporte lleva la cartera: lo cobrado en abonos del período y lo que deben hoy (spec 0008, RF-024)")
    void laCartera() {
        reportes.carteraSembrada = new CarteraDelPeriodo(Dinero.de(90_000), Dinero.de(60_000), Dinero.de(320_000), 4);

        ReporteDeResultados reporte = consultar.ejecutar(DatosDeReporte.LUNES_14, DatosDeReporte.DOMINGO_20,
                ModoGastosDelMes.REPARTIDOS, admin);

        // Un abono es un cobro, no una venta: no entra en las cifras del período.
        assertThat(reporte.cartera().cobrado()).isEqualTo(Dinero.de(150_000));
        assertThat(reporte.cartera().porCobrar()).isEqualTo(Dinero.de(320_000));
        assertThat(reporte.cartera().clientesQueDeben()).isEqualTo(4);
        assertThat(reporte.resultados().cifras().ventasNetas()).isEqualTo(Dinero.CERO);
    }

    @Test
    @DisplayName("sin fiado, la cartera viene vacía y el reporte no la calla")
    void carteraVacia() {
        ReporteDeResultados reporte = consultar.ejecutar(DatosDeReporte.LUNES_14, DatosDeReporte.DOMINGO_20,
                ModoGastosDelMes.REPARTIDOS, admin);

        assertThat(reporte.cartera()).isEqualTo(CarteraDelPeriodo.VACIA);
    }

    @Test
    @DisplayName("los reportes son del administrador: el cajero no los ve (spec 0004)")
    void elCajeroNo() {
        assertThatThrownBy(() -> consultar.ejecutar(LocalDate.of(2026, 9, 1), reloj.hoy(), null,
                ActoresDePrueba.cajero())).isInstanceOf(NoPermitidoException.class);
    }
}
