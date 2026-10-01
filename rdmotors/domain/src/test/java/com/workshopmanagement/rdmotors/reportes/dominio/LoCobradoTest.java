package com.workshopmanagement.rdmotors.reportes.dominio;

import static com.workshopmanagement.rdmotors.reportes.dominio.DatosDeReporte.conCosto;
import static com.workshopmanagement.rdmotors.reportes.dominio.DatosDeReporte.lasFilasSuman;
import static com.workshopmanagement.rdmotors.reportes.dominio.DatosDeReporte.sinCosto;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.reportes.dominio.ResultadosDelPeriodo.Cifras;

/** Lo cobrado de cada venta, repartido en sus renglones y su costo (spec 0014, decisiones 1 a 4). */
class LoCobradoTest {

    private static final LocalDate DIA_26 = LocalDate.of(2026, 9, 26);
    private static final LocalDate DIA_27 = LocalDate.of(2026, 9, 27);
    private static final LocalDate DIA_28 = LocalDate.of(2026, 9, 28);
    private static final LocalDate HOY = DIA_28;

    /**
     * Las ventas de producción del 26 al 28 de septiembre de 2026, con los mismos montos (los costos, al 60 %): tres de
     * contado, cinco fiadas y tres abonos. El de $101.000 quedó en la venta 8 ($90.000) y la 9 ($11.000).
     */
    private static DatosDeReporte septiembre() {
        DatosDeReporte datos = new DatosDeReporte();
        VentaCobrada v2 = datos.ventaFiada(DIA_26, 0, 65_000, conCosto("V2", 1, 65_000, 39_000));
        VentaCobrada v4 = datos.ventaFiada(DIA_28, 0, 45_000, conCosto("V4", 1, 45_000, 27_000));
        datos.venta(DIA_28, 0, 65_000, 0, conCosto("V5", 1, 65_000, 39_000));
        datos.venta(DIA_28, 0, 65_000, 0, conCosto("V7", 1, 65_000, 39_000));
        VentaCobrada v8 = datos.ventaFiada(DIA_28, 0, 90_000, conCosto("V8", 1, 90_000, 54_000));
        VentaCobrada v9 = datos.ventaFiada(DIA_28, 0, 55_000, conCosto("V9", 1, 55_000, 33_000));
        datos.ventaFiada(DIA_28, 0, 90_000, conCosto("V10", 1, 90_000, 54_000));
        datos.venta(DIA_28, 0, 85_850, 0, conCosto("V11", 1, 85_850, 51_510));
        datos.abono(v2, DIA_27, 65_000, FormaPago.TRANSFERENCIA);
        datos.abono(v4, DIA_28, 45_000);
        datos.abono(v8, DIA_28, 90_000);
        datos.abono(v9, DIA_28, 11_000);
        return datos;
    }

    @Test
    @DisplayName("LOS DATOS DE PRODUCCIÓN: el mes da $426.850 y no $560.850; el 28, $361.850; lo que falta ($134.000) no está")
    void losDatosDeProduccion() {
        DatosDeReporte datos = septiembre();

        ResultadosDelPeriodo mes = datos.calcular(LocalDate.of(2026, 9, 1), DIA_28, HOY);
        assertThat(mes.cifras().ventasNetas()).isEqualTo(Dinero.de(426_850));
        assertThat(mes.cifras().efectivo().mas(mes.cifras().transferencia())).isEqualTo(Dinero.de(426_850));
        assertThat(mes.cifras().deAbonos()).isEqualTo(Dinero.de(211_000));
        assertThat(mes.cifras().ventas()).as("tres de contado y tres fiadas pagadas; la 9 y la 10 no").isEqualTo(6);
        lasFilasSuman(mes);

        Cifras dia28 = datos.calcular(DIA_28, DIA_28, HOY).cifras();
        assertThat(dia28.ventasNetas()).isEqualTo(Dinero.de(361_850));
        assertThat(dia28.ventas()).isEqualTo(5);

        Cifras dia27 = datos.calcular(DIA_27, DIA_27, HOY).cifras();
        assertThat(dia27.ventasNetas()).as("el abono de la venta 2, el día que entró").isEqualTo(Dinero.de(65_000));
        assertThat(dia27.ventas()).as("ese día se terminó de pagar").isEqualTo(1);
        assertThat(dia27.costoVendido()).isEqualTo(Dinero.de(39_000));

        assertThat(datos.calcular(DIA_26, DIA_26, HOY).cifras().ventasNetas())
                .as("el 26 se vendió fiada y no entró nada").isEqualTo(Dinero.CERO);
    }

    @Test
    @DisplayName("una venta a medio pagar: el 20 % cobrado trae el 20 % del renglón y del costo, y la venta no se cuenta")
    void aMedioPagar() {
        DatosDeReporte datos = new DatosDeReporte();
        VentaCobrada v9 = datos.ventaFiada(DIA_28, 0, 55_000, conCosto("V9", 2, 55_000, 33_000));
        datos.abono(v9, DIA_28, 11_000);

        ResultadosDelPeriodo r = datos.calcular(DIA_28, DIA_28, HOY);

        assertThat(r.cifras().ventasNetas()).isEqualTo(Dinero.de(11_000));
        assertThat(r.cifras().costoVendido()).isEqualTo(Dinero.de(6_600));
        assertThat(r.cifras().ventas()).isZero();
        assertThat(r.cifras().unidades()).isZero();
        assertThat(r.cifras().ticketPromedio()).as("sin ventas completas, sin ticket").isNull();
        assertThat(r.repuestos()).singleElement().satisfies(v -> {
            assertThat(v.ventasNetas()).isEqualTo(Dinero.de(11_000));
            assertThat(v.unidades()).isZero();
            assertThat(v.utilidad()).isEqualTo(Dinero.de(4_400));
        });
        lasFilasSuman(r);
    }

    @Test
    @DisplayName("POR ACUMULADO: tres abonos de $33.333 a una venta de dos renglones con costos impares no dejan pesos sueltos")
    void porAcumulado() {
        DatosDeReporte datos = new DatosDeReporte();
        VentaCobrada venta = datos.ventaFiada(DIA_26, 0, 100_000,
                conCosto("A", 1, 70_001, 41_117), conCosto("B", 3, 29_999, 17_777));
        datos.abono(venta, DIA_26, 33_333);
        datos.abono(venta, DIA_27, 33_333);
        datos.abono(venta, DIA_28, 33_334);

        List<LoCobrado.Cobrado> cobrados = LoCobrado.de(datos.ventas, datos.renglones, datos.cobros);

        assertThat(cobrados).hasSize(3);
        for (LoCobrado.Cobrado c : cobrados) {
            assertThat(DatosDeReporte.suma(c.pedazos(), LoCobrado.Pedazo::neto)).as("las partes suman lo cobrado")
                    .isEqualTo(c.monto());
        }
        assertThat(cobrados).extracting(LoCobrado.Cobrado::completa).containsExactly(false, false, true);
        Dinero costoTotal = cobrados.stream().flatMap(c -> c.pedazos().stream()).map(LoCobrado.Pedazo::costo)
                .reduce(Dinero.CERO, Dinero::mas);
        assertThat(costoTotal).isEqualTo(Dinero.de(41_117 + 17_777));
        Dinero netoDeA = cobrados.stream().map(c -> c.pedazos().getFirst().neto()).reduce(Dinero.CERO, Dinero::mas);
        assertThat(netoDeA).as("el renglón entero, al peso").isEqualTo(Dinero.de(70_001));

        ResultadosDelPeriodo tresDias = datos.calcular(DIA_26, DIA_28, HOY);
        assertThat(tresDias.cifras().costoVendido()).isEqualTo(Dinero.de(58_894));
        assertThat(tresDias.cifras().unidades()).isEqualTo(4);
        lasFilasSuman(tresDias);
    }

    @Test
    @DisplayName("con descuento: lo cobrado lleva su parte de los renglones y del descuento, y renglones − descuentos = ventas netas")
    void conDescuento() {
        DatosDeReporte datos = new DatosDeReporte();
        datos.venta(DIA_28, 3_600, 32_400, 0, conCosto("F", 1, 24_000, 15_000), conCosto("P", 1, 12_000, 7_000));

        Cifras c = datos.calcular(DIA_28, DIA_28, HOY).cifras();

        assertThat(c.renglones()).isEqualTo(Dinero.de(36_000));
        assertThat(c.descuentos()).isEqualTo(Dinero.de(3_600));
        assertThat(c.ventasNetas()).isEqualTo(Dinero.de(32_400));
        assertThat(c.ventasConDescuento()).isEqualTo(1);
    }

    @Test
    @DisplayName("un abono a una fiada con descuento: su parte del descuento cuenta, y la venta con él (no 'ningún descuento')")
    void abonoAUnaFiadaConDescuento() {
        DatosDeReporte datos = new DatosDeReporte();
        VentaCobrada fiada = datos.ventaFiada(DIA_26, 10_000, 0, 90_000, conCosto("M", 1, 100_000, 60_000));
        datos.abono(fiada, DIA_28, 45_000);

        Cifras c = datos.calcular(DIA_28, DIA_28, HOY).cifras();

        assertThat(c.ventas()).as("no se ha terminado de pagar").isZero();
        assertThat(c.descuentos()).as("la mitad del descuento, con la mitad de lo fiado").isEqualTo(Dinero.de(5_000));
        assertThat(c.ventasConDescuento()).as("la venta de ese descuento").isEqualTo(1);
        assertThat(c.renglones().menos(c.descuentos())).isEqualTo(c.ventasNetas());
    }

    @Test
    @DisplayName("una venta de $0 (regalada con el descuento) se completa al cobrarse, con todo su costo")
    void ventaDeCero() {
        DatosDeReporte datos = new DatosDeReporte();
        datos.venta(DIA_28, 10_000, 0, 0, conCosto("R", 1, 10_000, 6_000));

        Cifras c = datos.calcular(DIA_28, DIA_28, HOY).cifras();

        assertThat(c.ventas()).isEqualTo(1);
        assertThat(c.ventasNetas()).isEqualTo(Dinero.CERO);
        assertThat(c.costoVendido()).isEqualTo(Dinero.de(6_000));
        assertThat(c.descuentos()).isEqualTo(Dinero.de(10_000));
    }

    @Test
    @DisplayName("lo que estaba a favor y se aplica a una venta nueva cuenta el día de esa venta; un renglón sin costo se avisa")
    void aFavorYSinCosto() {
        DatosDeReporte datos = new DatosDeReporte();
        VentaCobrada nueva = datos.ventaFiada(DIA_28, 0, 20_000, sinCosto("S", 1, 20_000));
        datos.abono(nueva, DIA_26, 20_000);

        ResultadosDelPeriodo dia28 = datos.calcular(DIA_28, DIA_28, HOY);
        assertThat(dia28.cifras().ventasNetas()).isEqualTo(Dinero.de(20_000));
        assertThat(dia28.sinCosto().renglones()).isEqualTo(1);
        assertThat(dia28.sinCosto().vendido()).isEqualTo(Dinero.de(20_000));
        assertThat(datos.calcular(DIA_26, DIA_26, HOY).cifras().ventasNetas()).isEqualTo(Dinero.CERO);
    }
}
