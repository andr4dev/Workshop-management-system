package com.workshopmanagement.rdmotors.reportes.dominio;

import static com.workshopmanagement.rdmotors.reportes.dominio.DatosDeReporte.DOMINGO_20;
import static com.workshopmanagement.rdmotors.reportes.dominio.DatosDeReporte.LUNES_14;
import static com.workshopmanagement.rdmotors.reportes.dominio.DatosDeReporte.conCosto;
import static com.workshopmanagement.rdmotors.reportes.dominio.DatosDeReporte.lasFilasSuman;
import static com.workshopmanagement.rdmotors.reportes.dominio.DatosDeReporte.sinCosto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.caja.dominio.NaturalezaGasto;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.reportes.dominio.ResultadosDelPeriodo.Cifras;
import com.workshopmanagement.rdmotors.reportes.dominio.ResultadosDelPeriodo.Fila;
import com.workshopmanagement.rdmotors.reportes.dominio.ResultadosDelPeriodo.PorCategoria;
import com.workshopmanagement.rdmotors.reportes.dominio.ResultadosDelPeriodo.Vendidos;

/** Las cifras de un período y sus partes (spec 0007, RF-004 a RF-018). */
class ResultadosDelPeriodoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 5);
    private static final LocalDate OCTUBRE_1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate OCTUBRE_31 = LocalDate.of(2026, 10, 31);

    @Test
    @DisplayName("EL EJEMPLO DEL §2: $1.220.000 de ventas netas, $420.000 de utilidad bruta (34,4 %), $100.000 de gastos y $320.000 de ganancia (26,2 %)")
    void ejemploDelSpec() {
        ResultadosDelPeriodo r = DatosDeReporte.semanaDelEjemplo()
                .calcular(LUNES_14, DOMINGO_20, HOY);
        Cifras c = r.cifras();

        assertThat(c.ventas()).isEqualTo(48);
        assertThat(c.unidades()).isEqualTo(132);
        assertThat(c.renglones()).isEqualTo(Dinero.de(1_250_000));
        assertThat(c.descuentos()).isEqualTo(Dinero.de(30_000));
        assertThat(c.ventasConDescuento()).isEqualTo(6);
        assertThat(c.ventasNetas()).isEqualTo(Dinero.de(1_220_000));
        assertThat(c.ticketPromedio()).isEqualTo(Dinero.de(25_417));
        assertThat(c.efectivo()).isEqualTo(Dinero.de(820_000));
        assertThat(c.transferencia()).isEqualTo(Dinero.de(400_000));
        assertThat(c.costoVendido()).isEqualTo(Dinero.de(780_000));
        assertThat(c.costosAdicionales()).isEqualTo(Dinero.de(20_000));
        assertThat(c.utilidadBruta()).isEqualTo(Dinero.de(420_000));
        assertThat(c.margenBruto()).isEqualByComparingTo("34.4");
        assertThat(c.gastos()).isEqualTo(Dinero.de(100_000));
        assertThat(c.utilidadOperativa()).isEqualTo(Dinero.de(320_000));
        assertThat(c.margenOperativo()).isEqualByComparingTo("26.2");

        assertThat(r.costosPorCategoria()).extracting(PorCategoria::categoria, PorCategoria::monto)
                .containsExactly(tuple("Fletes de mercancía", Dinero.de(20_000)));
        assertThat(r.gastosPorCategoria()).extracting(PorCategoria::categoria, PorCategoria::monto).containsExactly(
                tuple("Alimentación", Dinero.de(60_000)),
                tuple("Aseo y cafetería", Dinero.de(25_000)),
                tuple("Papelería", Dinero.de(15_000)));
        assertThat(r.sinCosto().renglones()).isZero();
        assertThat(r.gastosDelMes().incluidos()).isEqualTo(Dinero.CERO);
    }

    @Test
    @DisplayName("las partes suman: renglones − descuentos, efectivo + transferencia, las utilidades y las categorías")
    void lasPartesSuman() {
        ResultadosDelPeriodo r = DatosDeReporte.semanaDelEjemplo()
                .calcular(LUNES_14, DOMINGO_20, HOY);
        Cifras c = r.cifras();

        assertThat(c.renglones().menos(c.descuentos())).isEqualTo(c.ventasNetas());
        assertThat(c.efectivo().mas(c.transferencia())).isEqualTo(c.ventasNetas());
        assertThat(c.deAbonos()).as("todo de contado").isEqualTo(Dinero.CERO);
        assertThat(c.ventasNetas().menos(c.costoVendido()).menos(c.costosAdicionales())).isEqualTo(c.utilidadBruta());
        assertThat(c.utilidadBruta().menos(c.gastos())).isEqualTo(c.utilidadOperativa());
        assertThat(DatosDeReporte.suma(r.costosPorCategoria(), PorCategoria::monto)).isEqualTo(c.costosAdicionales());
        assertThat(DatosDeReporte.suma(r.gastosPorCategoria(), PorCategoria::monto)).isEqualTo(c.gastos());
    }

    @Test
    @DisplayName("SPEC 0014: lo fiado cuenta cuando se cobra y en la parte que se cobra, con su parte del costo; la venta se cuenta el día que se termina de pagar")
    void loFiadoCuentaAlCobrarse() {
        DatosDeReporte datos = new DatosDeReporte();
        VentaCobrada fiada = datos.ventaFiada(LUNES_14, 30_000, 50_000, DatosDeReporte.conCosto("F1", 1, 80_000, 60_000));
        datos.venta(LUNES_14, 0, 20_000, 0, DatosDeReporte.conCosto("F2", 1, 20_000, 12_000));
        LocalDate miercoles = LUNES_14.plusDays(2);
        datos.abono(fiada, miercoles, 50_000, FormaPago.TRANSFERENCIA);

        Cifras lunes = datos.calcular(LUNES_14, LUNES_14, HOY).cifras();
        assertThat(lunes.ventasNetas()).as("los $30.000 de la fiada y la de contado").isEqualTo(Dinero.de(50_000));
        assertThat(lunes.costoVendido()).as("3/8 de $60.000 y los $12.000").isEqualTo(Dinero.de(22_500 + 12_000));
        assertThat(lunes.ventas()).as("la fiada todavía no se cuenta").isEqualTo(1);
        assertThat(lunes.unidades()).isEqualTo(1);
        assertThat(lunes.efectivo().mas(lunes.transferencia())).isEqualTo(lunes.ventasNetas());

        Cifras miercolesC = datos.calcular(miercoles, miercoles, HOY).cifras();
        assertThat(miercolesC.ventasNetas()).isEqualTo(Dinero.de(50_000));
        assertThat(miercolesC.costoVendido()).as("el resto del costo").isEqualTo(Dinero.de(37_500));
        assertThat(miercolesC.ventas()).as("se terminó de pagar: ese día cuenta").isEqualTo(1);
        assertThat(miercolesC.transferencia()).isEqualTo(Dinero.de(50_000));
        assertThat(miercolesC.deAbonos()).isEqualTo(Dinero.de(50_000));
        assertThat(miercolesC.ticketPromedio()).as("el total de la que se completó").isEqualTo(Dinero.de(80_000));

        Cifras semana = datos.calcular(LUNES_14, DOMINGO_20, HOY).cifras();
        assertThat(semana.ventasNetas()).isEqualTo(Dinero.de(100_000));
        assertThat(semana.costoVendido()).as("completo: ni un peso de más ni de menos").isEqualTo(Dinero.de(72_000));
        assertThat(semana.utilidadBruta()).isEqualTo(Dinero.de(28_000));
        assertThat(semana.ventas()).isEqualTo(2);
    }

    @Test
    @DisplayName("día por día: la semana y el lunes siguiente son ocho filas, un día sin ventas en $0, y la fila del lunes con sus ventas y su almuerzo; suman las cifras")
    void diaPorDia() {
        DatosDeReporte datos = DatosDeReporte.semanaDelEjemplo();
        ResultadosDelPeriodo r = datos.calcular(LUNES_14, DOMINGO_20.plusDays(1), HOY);

        assertThat(r.agrupacion()).isEqualTo(Agrupacion.DIA);
        assertThat(r.filas()).hasSize(8);
        Fila lunes = r.filas().get(0);
        assertThat(lunes.desde()).isEqualTo(LUNES_14);
        assertThat(lunes.hasta()).isEqualTo(LUNES_14);
        assertThat(lunes.ventas()).isEqualTo(7);
        assertThat(lunes.ventasNetas()).isEqualTo(Dinero.de(195_000));
        assertThat(lunes.costoVendido()).isEqualTo(Dinero.de(135_000));
        assertThat(lunes.utilidadBruta()).isEqualTo(Dinero.de(60_000));
        assertThat(lunes.gastos()).isEqualTo(Dinero.de(20_000));
        assertThat(lunes.utilidadOperativa()).isEqualTo(Dinero.de(40_000));

        Fila lunesSiguiente = r.filas().get(7);
        assertThat(lunesSiguiente.ventas()).isZero();
        assertThat(lunesSiguiente.ventasNetas()).isEqualTo(Dinero.CERO);
        assertThat(lunesSiguiente.utilidadOperativa()).isEqualTo(Dinero.de(-99_000));
        lasFilasSuman(r);
    }

    @Test
    @DisplayName("RF-007: un renglón sin costo no suma $0 al costo: se cuenta aparte, con lo que se cobró por él con su parte del descuento")
    void renglonSinCosto() {
        DatosDeReporte datos = new DatosDeReporte();
        datos.venta(LUNES_14, 3_600, 32_400, 0, conCosto("FILTRO", 1, 24_000, 15_000), sinCosto("PASTILLAS", 3, 12_000));
        datos.venta(LUNES_14.plusDays(1), 0, 12_000, 0, sinCosto("PASTILLAS", 3, 12_000));

        ResultadosDelPeriodo r = datos.calcular(LUNES_14, DOMINGO_20, HOY);

        assertThat(r.cifras().costoVendido()).isEqualTo(Dinero.de(15_000));
        assertThat(r.cifras().utilidadBruta()).isEqualTo(Dinero.de(44_400 - 15_000));
        assertThat(r.sinCosto().renglones()).isEqualTo(2);
        assertThat(r.sinCosto().unidades()).isEqualTo(6);
        assertThat(r.sinCosto().vendido()).isEqualTo(Dinero.de(10_800 + 12_000));
        assertThat(r.sinCosto().repuestos()).singleElement().satisfies(s -> {
            assertThat(s.codigo()).isEqualTo("PASTILLAS");
            assertThat(s.varianteId()).isEqualTo(datos.id("PASTILLAS"));
            assertThat(s.unidades()).isEqualTo(6);
            assertThat(s.vendido()).isEqualTo(Dinero.de(22_800));
        });
        assertThat(r.filas().get(0).renglonesSinCosto()).isEqualTo(1);
        lasFilasSuman(r);
    }

    @Test
    @DisplayName("sin ventas y con gastos: ventas $0, ganancia negativa, márgenes y ticket en blanco (no 0 %)")
    void sinVentasConGastos() {
        DatosDeReporte datos = new DatosDeReporte();
        datos.gasto(LUNES_14, "Papelería", NaturalezaGasto.GASTO, 15_000);

        ResultadosDelPeriodo r = datos.calcular(LUNES_14, DOMINGO_20, HOY);

        assertThat(r.cifras().ventasNetas()).isEqualTo(Dinero.CERO);
        assertThat(r.cifras().utilidadOperativa()).isEqualTo(Dinero.de(-15_000));
        assertThat(r.cifras().margenBruto()).isNull();
        assertThat(r.cifras().margenOperativo()).isNull();
        assertThat(r.cifras().ticketPromedio()).isNull();
        lasFilasSuman(r);
    }

    @Test
    @DisplayName("un retiro o una compra no son gasto: el reporte solo conoce los gastos y sus categorías de costo o de gasto")
    void costoOGasto() {
        DatosDeReporte datos = new DatosDeReporte();
        datos.venta(LUNES_14, 0, 100_000, 0, conCosto("FILTRO", 1, 100_000, 60_000));
        datos.gasto(LUNES_14, "Fletes de mercancía", NaturalezaGasto.COSTO, 5_000);
        datos.gasto(LUNES_14, "Alimentación", NaturalezaGasto.GASTO, 12_000);

        Cifras c = datos.calcular(LUNES_14, LUNES_14, HOY).cifras();

        assertThat(c.utilidadBruta()).as("el costo resta en la bruta").isEqualTo(Dinero.de(35_000));
        assertThat(c.utilidadOperativa()).as("el gasto, en la operativa").isEqualTo(Dinero.de(23_000));
    }

    @Test
    @DisplayName("RF-010a REPARTIDOS: el arriendo de octubre carga $25.807 el día 1, $800.000 el mes completo, y aunque su fecha quede fuera de la semana")
    void arriendoRepartido() {
        DatosDeReporte datos = new DatosDeReporte();
        datos.gastoDelMes(OCTUBRE_1, "Arriendo", 800_000);
        datos.gastoDelMes(LocalDate.of(2026, 9, 1), "Arriendo", 800_000);

        ResultadosDelPeriodo primero = datos.calcular(OCTUBRE_1, OCTUBRE_1, HOY);
        assertThat(primero.cifras().gastos()).isEqualTo(Dinero.de(25_807));
        assertThat(primero.gastosDelMes().incluidos()).isEqualTo(Dinero.de(25_807));
        assertThat(primero.gastosDelMes().fuera()).isEqualTo(Dinero.CERO);
        assertThat(primero.filaGastosDelMes()).isNull();

        ResultadosDelPeriodo octubre = datos.calcular(OCTUBRE_1, OCTUBRE_31, HOY);
        assertThat(octubre.cifras().gastos()).isEqualTo(Dinero.de(800_000));
        assertThat(octubre.gastosPorCategoria()).singleElement()
                .satisfies(g -> assertThat(g.monto()).isEqualTo(Dinero.de(800_000)));
        lasFilasSuman(octubre);

        ResultadosDelPeriodo semana = datos.calcular(LUNES_14, DOMINGO_20, HOY);
        assertThat(semana.cifras().gastos()).isEqualTo(Dinero.de(7 * 26_667));
        lasFilasSuman(semana);

        ResultadosDelPeriodo cruzaElMes = datos.calcular(LocalDate.of(2026, 9, 25), LocalDate.of(2026, 10, 5), HOY);
        assertThat(cruzaElMes.cifras().gastos()).isEqualTo(Dinero.de(6 * 26_666 + 5 * 25_807));
        lasFilasSuman(cruzaElMes);
    }

    @Test
    @DisplayName("DEL MES, EN UN DÍA (spec 0014): el 1 de octubre y una semana no lo cargan y dicen cuánto quedó fuera; octubre completo, entero en su fila")
    void arriendoEnteroEnSuMes() {
        DatosDeReporte datos = new DatosDeReporte();
        datos.gastoDelMesEntero(OCTUBRE_1, "Arriendo", 800_000);
        datos.gasto(OCTUBRE_1, "Papelería", NaturalezaGasto.GASTO, 15_000);

        ResultadosDelPeriodo primero = datos.calcular(OCTUBRE_1, OCTUBRE_1, HOY);
        assertThat(primero.cifras().gastos()).as("el de papelería sí, en su fecha").isEqualTo(Dinero.de(15_000));
        assertThat(primero.gastosDelMes().fuera()).isEqualTo(Dinero.de(800_000));
        assertThat(primero.gastosDelMes().incluidos()).isEqualTo(Dinero.CERO);
        assertThat(primero.filaGastosDelMes()).isNull();

        ResultadosDelPeriodo semana = datos.calcular(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11), HOY);
        assertThat(semana.cifras().gastos()).isEqualTo(Dinero.CERO);
        assertThat(semana.gastosDelMes().fuera()).isEqualTo(Dinero.de(800_000));

        ResultadosDelPeriodo octubre = datos.calcular(OCTUBRE_1, OCTUBRE_31, HOY);
        assertThat(octubre.cifras().gastos()).isEqualTo(Dinero.de(815_000));
        assertThat(octubre.gastosDelMes().incluidos()).isEqualTo(Dinero.de(800_000));
        assertThat(octubre.gastosDelMes().fuera()).isEqualTo(Dinero.CERO);
        assertThat(octubre.filaGastosDelMes().gastos()).isEqualTo(Dinero.de(800_000));
        assertThat(octubre.filaGastosDelMes().utilidadOperativa()).isEqualTo(Dinero.de(-800_000));
        assertThat(DatosDeReporte.suma(octubre.filas(), Fila::gastos)).as("los días, solo la papelería")
                .isEqualTo(Dinero.de(15_000));
        lasFilasSuman(octubre);

        ResultadosDelPeriodo mesEnCurso = datos.calcular(OCTUBRE_1, LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 15));
        assertThat(mesEnCurso.cifras().gastos()).as("del 1 a hoy cubre el mes en curso").isEqualTo(Dinero.de(815_000));
        lasFilasSuman(mesEnCurso);
    }

    @Test
    @DisplayName("CADA GASTO DICE CÓMO CUENTA (spec 0014): uno repartido y uno entero del mismo mes, en un día, en una semana y en el mes")
    void repartidoYEnteroJuntos() {
        DatosDeReporte datos = new DatosDeReporte();
        datos.gastoDelMes(OCTUBRE_1, "Servicios públicos", 310_000);
        datos.gastoDelMesEntero(OCTUBRE_1, "Arriendo", 800_000);

        ResultadosDelPeriodo dia = datos.calcular(LocalDate.of(2026, 10, 7), LocalDate.of(2026, 10, 7), HOY);
        assertThat(dia.cifras().gastos()).as("solo la cuota de los servicios").isEqualTo(Dinero.de(10_000));
        assertThat(dia.gastosDelMes().incluidos()).isEqualTo(Dinero.de(10_000));
        assertThat(dia.gastosDelMes().fuera()).as("el arriendo, entero en el mes").isEqualTo(Dinero.de(800_000));
        assertThat(dia.filaGastosDelMes()).isNull();
        lasFilasSuman(dia);

        ResultadosDelPeriodo semana = datos.calcular(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11), HOY);
        assertThat(semana.cifras().gastos()).isEqualTo(Dinero.de(70_000));
        assertThat(semana.gastosDelMes().fuera()).isEqualTo(Dinero.de(800_000));
        lasFilasSuman(semana);

        ResultadosDelPeriodo octubre = datos.calcular(OCTUBRE_1, OCTUBRE_31, HOY);
        assertThat(octubre.cifras().gastos()).isEqualTo(Dinero.de(1_110_000));
        assertThat(octubre.gastosDelMes().incluidos()).isEqualTo(Dinero.de(1_110_000));
        assertThat(octubre.gastosDelMes().fuera()).isEqualTo(Dinero.CERO);
        assertThat(octubre.filaGastosDelMes().gastos()).as("solo el entero va en su fila").isEqualTo(Dinero.de(800_000));
        assertThat(DatosDeReporte.suma(octubre.filas(), Fila::gastos)).as("los días, las cuotas")
                .isEqualTo(Dinero.de(310_000));
        assertThat(octubre.gastosPorCategoria()).extracting(g -> g.categoria(), g -> g.monto().valor().longValueExact())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Arriendo", 800_000L),
                        org.assertj.core.groups.Tuple.tuple("Servicios públicos", 310_000L));
        lasFilasSuman(octubre);

        // Ver cálculo (spec 0014, RF-008): cada categoría trae sus gastos, con lo que cargan en el período.
        ResultadosDelPeriodo.GastoCargado servicios = semana.gastosPorCategoria().getFirst().gastos().getFirst();
        assertThat(servicios.monto()).isEqualTo(Dinero.de(310_000));
        assertThat(servicios.cargado()).isEqualTo(Dinero.de(70_000));
        assertThat(servicios.dias()).isEqualTo(7);
        assertThat(servicios.diasDelMes()).isEqualTo(31);
        assertThat(octubre.gastosPorCategoria().getFirst().gastos()).singleElement().satisfies(arriendo -> {
            assertThat(arriendo.cargado()).as("entero").isEqualTo(Dinero.de(800_000));
            assertThat(arriendo.dias()).isZero();
        });
        for (ResultadosDelPeriodo.PorCategoria c : octubre.gastosPorCategoria()) {
            assertThat(DatosDeReporte.suma(c.gastos(), ResultadosDelPeriodo.GastoCargado::cargado))
                    .as("los gastos suman su categoría").isEqualTo(c.monto());
        }
    }

    @Test
    @DisplayName("un gasto del mes de costo resta en la utilidad bruta, repartido o entero")
    void gastoDelMesDeCosto() {
        DatosDeReporte repartido = new DatosDeReporte();
        repartido.gastoDelMes(OCTUBRE_1, "Bodega", NaturalezaGasto.COSTO, 310_000, true);
        ResultadosDelPeriodo delDia = repartido.calcular(OCTUBRE_1, OCTUBRE_1, HOY);
        assertThat(delDia.cifras().costosAdicionales()).isEqualTo(Dinero.de(10_000));
        assertThat(delDia.cifras().gastos()).isEqualTo(Dinero.CERO);

        DatosDeReporte entero = new DatosDeReporte();
        entero.gastoDelMes(OCTUBRE_1, "Bodega", NaturalezaGasto.COSTO, 310_000, false);
        ResultadosDelPeriodo delMes = entero.calcular(OCTUBRE_1, OCTUBRE_31, HOY);
        assertThat(delMes.cifras().utilidadBruta()).isEqualTo(Dinero.de(-310_000));
        assertThat(delMes.filaGastosDelMes().costosAdicionales()).isEqualTo(Dinero.de(310_000));
        lasFilasSuman(delMes);
    }

    @Test
    @DisplayName("RF-018: 90 días van por semanas de lunes a domingo, recortadas al período, y suman")
    void noventaDiasPorSemanas() {
        DatosDeReporte datos = new DatosDeReporte();
        LocalDate miercoles = LocalDate.of(2026, 6, 3);
        LocalDate lunes = LocalDate.of(2026, 8, 31);
        datos.venta(LocalDate.of(2026, 6, 7), 0, 10_000, 0, conCosto("FILTRO", 1, 10_000, 6_000));
        datos.venta(lunes, 0, 20_000, 0, conCosto("FILTRO", 2, 20_000, 12_000));
        datos.gasto(LocalDate.of(2026, 6, 8), "Papelería", NaturalezaGasto.GASTO, 3_000);

        ResultadosDelPeriodo r = datos.calcular(miercoles, lunes, HOY);

        assertThat(new Periodo(miercoles, lunes).dias()).isEqualTo(90);
        assertThat(r.agrupacion()).isEqualTo(Agrupacion.SEMANA);
        assertThat(r.filas().get(0)).satisfies(f -> {
            assertThat(f.desde()).isEqualTo(miercoles);
            assertThat(f.hasta()).isEqualTo(LocalDate.of(2026, 6, 7));
            assertThat(f.ventasNetas()).isEqualTo(Dinero.de(10_000));
            assertThat(f.gastos()).isEqualTo(Dinero.CERO);
        });
        assertThat(r.filas().get(1).gastos()).isEqualTo(Dinero.de(3_000));
        assertThat(r.filas().getLast()).satisfies(f -> {
            assertThat(f.desde()).isEqualTo(lunes);
            assertThat(f.hasta()).isEqualTo(lunes);
            assertThat(f.ventasNetas()).isEqualTo(Dinero.de(20_000));
        });
        lasFilasSuman(r);
    }

    @Test
    @DisplayName("un cobro de una venta que no vino, una venta sin renglones o lo cobrado de más no se callan: son un error de lectura")
    void filasQueNoCuadran() {
        DatosDeReporte datos = new DatosDeReporte();
        VentaCobrada lunes = datos.venta(LUNES_14, 0, 10_000, 0, conCosto("FILTRO", 1, 10_000, 6_000));
        Periodo delLunes = new Periodo(LUNES_14, LUNES_14);

        assertThatThrownBy(() -> ResultadosDelPeriodo.calcular(delLunes, List.of(), datos.renglones, datos.cobros,
                List.of(), HOY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ResultadosDelPeriodo.calcular(delLunes, List.of(lunes), List.of(), datos.cobros,
                List.of(), HOY)).isInstanceOf(IllegalArgumentException.class);
        datos.abono(lunes, LUNES_14, 1);
        assertThatThrownBy(() -> ResultadosDelPeriodo.calcular(delLunes, List.of(lunes), datos.renglones, datos.cobros,
                List.of(), HOY)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("le entraron");
    }

    @Test
    @DisplayName("un margen se redondea a un decimal: $1 de $3 es 33,3 %")
    void margenConUnDecimal() {
        DatosDeReporte datos = new DatosDeReporte();
        datos.venta(LUNES_14, 0, 3, 0, conCosto("TORNILLO", 1, 3, 2));

        assertThat(datos.calcular(LUNES_14, LUNES_14, HOY).cifras().margenBruto())
                .isEqualTo(new BigDecimal("33.3"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  P2 — repuestos y categorías
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RF-019: cada repuesto con sus unidades, ventas netas, costo, utilidad y margen; el ranking suma las ventas netas")
    void rankingDeRepuestos() {
        ResultadosDelPeriodo r = DatosDeReporte.semanaDelEjemplo()
                .calcular(LUNES_14, DOMINGO_20, HOY);

        assertThat(r.repuestos()).extracting(Vendidos::codigo, Vendidos::unidades, Vendidos::ventasNetas,
                Vendidos::costo, Vendidos::utilidad, Vendidos::margen).containsExactly(
                tuple("PASTILLAS", 56, Dinero.de(700_000), Dinero.de(420_000), Dinero.de(280_000), new BigDecimal("40.0")),
                tuple("FILTRO-AIRE", 36, Dinero.de(420_000), Dinero.de(270_000), Dinero.de(150_000), new BigDecimal("35.7")),
                tuple("CAJA-ACEITE", 40, Dinero.de(100_000), Dinero.de(90_000), Dinero.de(10_000), new BigDecimal("10.0")));
        assertThat(DatosDeReporte.suma(r.repuestos(), Vendidos::ventasNetas)).isEqualTo(r.cifras().ventasNetas());
        assertThat(r.repuestos()).noneMatch(Vendidos::conPerdida);
    }

    @Test
    @DisplayName("DECISIÓN 3: el filtro de $24.000 y las pastillas de $12.000 con $3.600 de descuento quedan en $21.600 y $10.800")
    void descuentoRepartidoEnElRanking() {
        DatosDeReporte datos = new DatosDeReporte();
        datos.venta(LUNES_14, 3_600, 32_400, 0, conCosto("FILTRO", 1, 24_000, 15_000), conCosto("PASTILLAS", 1, 12_000, 6_000));

        ResultadosDelPeriodo r = datos.calcular(LUNES_14, LUNES_14, HOY);

        assertThat(r.repuestos()).extracting(Vendidos::codigo, Vendidos::ventasNetas, Vendidos::utilidad).containsExactly(
                tuple("FILTRO", Dinero.de(21_600), Dinero.de(6_600)),
                tuple("PASTILLAS", Dinero.de(10_800), Dinero.de(4_800)));
        assertThat(r.repuestos().getFirst().id()).isEqualTo(datos.id("FILTRO"));
    }

    @Test
    @DisplayName("RF-020: uno vendido a $25 con costo de $1.000 queda con pérdida")
    void conPerdida() {
        DatosDeReporte datos = new DatosDeReporte();
        datos.venta(LUNES_14, 0, 25, 0, conCosto("BUJIA", 1, 25, 1_000));
        datos.venta(LUNES_14, 0, 30_000, 0, conCosto("FILTRO", 1, 30_000, 20_000));

        ResultadosDelPeriodo r = datos.calcular(LUNES_14, LUNES_14, HOY);

        assertThat(r.repuestos()).filteredOn(Vendidos::conPerdida).singleElement().satisfies(v -> {
            assertThat(v.codigo()).isEqualTo("BUJIA");
            assertThat(v.utilidad()).isEqualTo(Dinero.de(-975));
            assertThat(v.margen()).isEqualByComparingTo("-3900.0");
        });
    }

    @Test
    @DisplayName("RF-019: si algún renglón de un repuesto se vendió sin costo, su utilidad y su margen quedan en blanco")
    void repuestoSinCostoSinUtilidad() {
        DatosDeReporte datos = new DatosDeReporte();
        datos.venta(LUNES_14, 0, 10_000, 0, conCosto("FILTRO", 1, 10_000, 6_000));
        datos.venta(LUNES_14, 0, 10_000, 0, sinCosto("FILTRO", 1, 10_000));

        Vendidos filtro = datos.calcular(LUNES_14, LUNES_14, HOY).repuestos().getFirst();

        assertThat(filtro.ventasNetas()).isEqualTo(Dinero.de(20_000));
        assertThat(filtro.costo()).isEqualTo(Dinero.de(6_000));
        assertThat(filtro.renglonesSinCosto()).isEqualTo(1);
        assertThat(filtro.utilidad()).isNull();
        assertThat(filtro.margen()).isNull();
        assertThat(filtro.conPerdida()).isFalse();
    }

    @Test
    @DisplayName("RF-021: por categoría de repuesto, con Sin categoría, y las categorías suman las ventas netas")
    void categoriasDeRepuesto() {
        DatosDeReporte datos = new DatosDeReporte();
        datos.venta(LUNES_14, 3_000, 45_000, 0, conCosto("FILTRO", 1, 24_000, 15_000).en("Filtros"),
                conCosto("PASTILLAS", 2, 24_000, 12_000).en("Frenos"));
        datos.venta(LUNES_14, 0, 18_000, 0, conCosto("FILTRO-AIRE", 1, 18_000, 9_000).en("Filtros"));
        datos.venta(LUNES_14, 0, 5_000, 0, conCosto("TORNILLO", 5, 5_000, 2_500));

        ResultadosDelPeriodo r = datos.calcular(LUNES_14, LUNES_14, HOY);

        assertThat(r.categoriasDeRepuesto()).extracting(Vendidos::nombre, Vendidos::unidades, Vendidos::ventasNetas,
                Vendidos::utilidad).containsExactly(
                tuple("Filtros", 2, Dinero.de(22_500 + 18_000), Dinero.de(40_500 - 24_000)),
                tuple("Frenos", 2, Dinero.de(22_500), Dinero.de(10_500)),
                tuple(ResultadosDelPeriodo.SIN_CATEGORIA, 5, Dinero.de(5_000), Dinero.de(2_500)));
        assertThat(r.categoriasDeRepuesto().getFirst().id()).isEqualTo(datos.id("Filtros"));
        assertThat(r.categoriasDeRepuesto().getLast().id()).isNull();
        assertThat(DatosDeReporte.suma(r.categoriasDeRepuesto(), Vendidos::ventasNetas)).isEqualTo(r.cifras().ventasNetas());
        assertThat(r.repuestos()).filteredOn(v -> v.codigo().equals("TORNILLO")).singleElement()
                .satisfies(v -> assertThat(v.categoria()).isEqualTo(ResultadosDelPeriodo.SIN_CATEGORIA));
    }
}
