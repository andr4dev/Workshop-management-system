package com.workshopmanagement.rdmotors.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.workshopmanagement.rdmotors.caja.aplicacion.AbrirTurno;
import com.workshopmanagement.rdmotors.caja.aplicacion.AnularGasto;
import com.workshopmanagement.rdmotors.caja.aplicacion.CerrarTurno;
import com.workshopmanagement.rdmotors.caja.aplicacion.ConsultarTurnos;
import com.workshopmanagement.rdmotors.caja.aplicacion.ComandoRegistrarGasto;
import com.workshopmanagement.rdmotors.caja.aplicacion.RegistrarCategoriaGasto;
import com.workshopmanagement.rdmotors.caja.aplicacion.RegistrarGasto;
import com.workshopmanagement.rdmotors.caja.dominio.NaturalezaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioCategoriasGasto;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compras.aplicacion.ComandoRegistrarCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarProveedor;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ComandoCrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.aplicacion.CrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioCategorias;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;
import com.workshopmanagement.rdmotors.reportes.aplicacion.ConsultarResultados;
import com.workshopmanagement.rdmotors.reportes.aplicacion.ReporteDeResultados;
import com.workshopmanagement.rdmotors.reportes.dominio.Agrupacion;
import com.workshopmanagement.rdmotors.reportes.dominio.ModoGastosDelMes;
import com.workshopmanagement.rdmotors.reportes.dominio.Periodo;
import com.workshopmanagement.rdmotors.reportes.dominio.ResultadosDelPeriodo;
import com.workshopmanagement.rdmotors.reportes.dominio.ResultadosDelPeriodo.Cifras;
import com.workshopmanagement.rdmotors.reportes.dominio.ResultadosDelPeriodo.Fila;
import com.workshopmanagement.rdmotors.reportes.dominio.ResultadosDelPeriodo.Vendidos;
import com.workshopmanagement.rdmotors.ventas.aplicacion.AnularVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.CobrarVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ComandoCobrarVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.ModoDescuento;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;

/**
 * Los reportes de resultados contra un Postgres real (spec 0007).
 *
 * <p>Clase y contenedor propios: un reporte suma todo lo del período, y compartir base con otras pruebas haría que
 * sus ventas cayeran en estos períodos.
 *
 * <p>Las ventas se cobran con los casos de uso de verdad, que las fechan hoy; después se <b>mueven por SQL</b> al día
 * que la prueba necesita. Cada prueba usa días que ninguna otra toca.
 */
@SpringBootTest
@Import(UsuariosDePrueba.class)
@Testcontainers
class ReportesIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired UsuariosDePrueba personas;
    @Autowired ConsultarResultados consultarResultados;
    @Autowired AbrirTurno abrirTurno;
    @Autowired RepositorioTurnos turnos;
    @Autowired CobrarVenta cobrarVenta;
    @Autowired AnularVenta anularVenta;
    @Autowired CrearRepuesto crearRepuesto;
    @Autowired RegistrarCompra registrarCompra;
    @Autowired RegistrarProveedor registrarProveedor;
    @Autowired RepositorioVariantes variantes;
    @Autowired RepositorioCategorias categorias;
    @Autowired RegistrarGasto registrarGasto;
    @Autowired AnularGasto anularGasto;
    @Autowired RegistrarCategoriaGasto registrarCategoria;
    @Autowired RepositorioCategoriasGasto categoriasGasto;
    @Autowired CerrarTurno cerrarTurno;
    @Autowired ConsultarTurnos consultarTurnos;
    @Autowired Reloj reloj;
    @Autowired JdbcTemplate jdbc;

    private final Actor cajero;

    /** Por el constructor y no en un {@code @BeforeEach}: así ya existe cuando corre cualquiera de ellos. */
    ReportesIntegracionTest(@Autowired UsuariosDePrueba personas) {
        this.cajero = personas.cajero();
    }

    @BeforeEach
    void conTurnoAbierto() {
        if (turnos.abierto().isEmpty()) {
            abrirTurno.ejecutar(Dinero.de(500_000), cajero);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Ayudas
    // ─────────────────────────────────────────────────────────────────────────

    private Variante repuesto(long precio, int stock, long costoTotal) {
        return repuesto(precio, stock, costoTotal, categorias.activas().getFirst().getId());
    }

    private Variante repuesto(long precio, int stock, long costoTotal, UUID categoriaId) {
        String codigo = "REP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Variante nuevo = crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo("REPUESTO " + codigo,
                categoriaId, null, codigo, "MARCA", Dinero.de(precio), 1), personas.administrador());
        comprar(nuevo, stock, costoTotal);
        return variantes.buscar(nuevo.getId()).orElseThrow();
    }

    private void comprar(Variante variante, int cantidad, long costoTotal) {
        var proveedor = registrarProveedor.ejecutar("Proveedor de reportes " + UUID.randomUUID(), null, null, personas.administrador());
        registrarCompra.ejecutar(new ComandoRegistrarCompra(proveedor.getId(), reloj.hoy(), null, FormaPago.EFECTIVO,
                null, personas.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porTotal(variante.getId(), cantidad, Dinero.de(costoTotal), null))));
    }

    private static ComandoCobrarVenta.Renglon renglon(Variante v, int cantidad) {
        return new ComandoCobrarVenta.Renglon(v.getId(), cantidad, v.getPrecio().valor().longValueExact());
    }

    /** Cobra una venta y la mueve al instante dado, en hora de Colombia. */
    private Venta venta(LocalDate dia, LocalTime hora, ComandoCobrarVenta.ComandoDescuento descuento,
                        List<ComandoCobrarVenta.Pago> pagos, ComandoCobrarVenta.Renglon... renglones) {
        Venta venta = cobrarVenta.ejecutar(new ComandoCobrarVenta(UUID.randomUUID(), List.of(renglones), descuento,
                pagos, cajero)).venta();
        jdbc.update("update venta set cobrada_en = ? where id = ?",
                dia.atTime(hora).atZone(Periodo.ZONA).toOffsetDateTime(), venta.getId());
        return venta;
    }

    private Venta enEfectivo(LocalDate dia, long total, ComandoCobrarVenta.Renglon... renglones) {
        return venta(dia, LocalTime.NOON, null, List.of(new ComandoCobrarVenta.Pago(FormaPago.EFECTIVO, total, null)),
                renglones);
    }

    private UUID categoriaGasto(String nombre, NaturalezaGasto naturaleza) {
        return categoriasGasto.buscarPorNombre(nombre)
                .orElseGet(() -> registrarCategoria.ejecutar(nombre, naturaleza, personas.administrador())).getId();
    }

    private ComandoRegistrarGasto porFuera(String categoria, NaturalezaGasto naturaleza, long monto, LocalDate fecha) {
        return ComandoRegistrarGasto.porFuera(UUID.randomUUID(), categoriaGasto(categoria, naturaleza),
                Dinero.de(monto), categoria, FormaPago.EFECTIVO, null, fecha, personas.administrador());
    }

    private ResultadosDelPeriodo resultados(LocalDate desde, LocalDate hasta) {
        return consultarResultados.ejecutar(desde, hasta, ModoGastosDelMes.REPARTIDOS, personas.administrador()).resultados();
    }

    private static void lasFilasSuman(ResultadosDelPeriodo r) {
        Cifras c = r.cifras();
        List<Fila> filas = r.filaGastosDelMes() == null ? r.filas()
                : java.util.stream.Stream.concat(r.filas().stream(), java.util.stream.Stream.of(r.filaGastosDelMes())).toList();
        assertThat(filas.stream().mapToInt(Fila::ventas).sum()).isEqualTo(c.ventas());
        assertThat(filas.stream().map(Fila::ventasNetas).reduce(Dinero.CERO, Dinero::mas)).isEqualTo(c.ventasNetas());
        assertThat(filas.stream().map(Fila::costoVendido).reduce(Dinero.CERO, Dinero::mas)).isEqualTo(c.costoVendido());
        assertThat(filas.stream().map(Fila::utilidadOperativa).reduce(Dinero.CERO, Dinero::mas))
                .isEqualTo(c.utilidadOperativa());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Las cifras
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("LAS CIFRAS con ventas, descuento, pagos, costos y gastos de verdad; la anulada sale del día en que se vendió; una compra posterior más cara no cambia el costo vendido")
    void lasCifras() {
        LocalDate lunes = reloj.hoy().minusDays(100);
        LocalDate martes = lunes.plusDays(1);
        Variante filtro = repuesto(24_000, 10, 100_000);     // costo $10.000 c/u
        Variante pastillas = repuesto(12_000, 10, 60_000);   // costo $6.000 c/u

        venta(lunes, LocalTime.of(9, 0),
                new ComandoCobrarVenta.ComandoDescuento(ModoDescuento.MONTO, new BigDecimal("3600"), "cliente frecuente"),
                List.of(new ComandoCobrarVenta.Pago(FormaPago.EFECTIVO, 20_000, null),
                        new ComandoCobrarVenta.Pago(FormaPago.TRANSFERENCIA, 12_400, null)),
                renglon(filtro, 1), renglon(pastillas, 1));
        enEfectivo(martes, 24_000, renglon(pastillas, 2));
        Venta seAnula = enEfectivo(martes, 24_000, renglon(filtro, 1));
        registrarGasto.ejecutar(porFuera("Alimentación", NaturalezaGasto.GASTO, 12_000, lunes));
        registrarGasto.ejecutar(porFuera("Fletes de mercancía", NaturalezaGasto.COSTO, 5_000, martes));
        var anulado = registrarGasto.ejecutar(porFuera("Papelería", NaturalezaGasto.GASTO, 99_000, lunes));
        anularGasto.ejecutar(anulado.getId(), "Se registró dos veces", personas.administrador());

        assertThat(resultados(lunes, martes).cifras().ventas()).as("antes de anular").isEqualTo(3);
        anularVenta.ejecutar(seAnula.getId(), "Cliente se arrepintió", cajero);

        ResultadosDelPeriodo r = resultados(lunes, martes);
        Cifras c = r.cifras();
        assertThat(c.ventas()).isEqualTo(2);
        assertThat(c.unidades()).isEqualTo(4);
        assertThat(c.renglones()).isEqualTo(Dinero.de(60_000));
        assertThat(c.descuentos()).isEqualTo(Dinero.de(3_600));
        assertThat(c.ventasNetas()).isEqualTo(Dinero.de(56_400));
        assertThat(c.efectivo()).isEqualTo(Dinero.de(44_000));
        assertThat(c.transferencia()).isEqualTo(Dinero.de(12_400));
        assertThat(c.costoVendido()).isEqualTo(Dinero.de(28_000));
        assertThat(c.costosAdicionales()).isEqualTo(Dinero.de(5_000));
        assertThat(c.utilidadBruta()).isEqualTo(Dinero.de(23_400));
        assertThat(c.gastos()).as("el anulado no cuenta").isEqualTo(Dinero.de(12_000));
        assertThat(c.utilidadOperativa()).isEqualTo(Dinero.de(11_400));
        assertThat(r.filas()).hasSize(2);
        assertThat(r.filas().get(0).ventasNetas()).isEqualTo(Dinero.de(32_400));
        assertThat(r.filas().get(0).costoVendido()).isEqualTo(Dinero.de(16_000));
        assertThat(r.filas().get(1).costosAdicionales()).isEqualTo(Dinero.de(5_000));
        assertThat(r.gastosPorCategoria()).singleElement().satisfies(g -> assertThat(g.categoria()).isEqualTo("Alimentación"));
        lasFilasSuman(r);

        comprar(filtro, 10, 300_000);   // ahora el filtro cuesta más: el promedio sube
        assertThat(variantes.buscar(filtro.getId()).orElseThrow().getCostoPromedio())
                .isGreaterThan(new BigDecimal("10000"));
        assertThat(resultados(lunes, martes).cifras().costoVendido()).as("costo al vender, no el de hoy")
                .isEqualTo(Dinero.de(28_000));
    }

    @Test
    @DisplayName("RF-002: una venta cobrada a las 7:30 p. m. de Colombia es de ese día; una a medianoche, del siguiente")
    void horaDeColombia() {
        LocalDate dia = reloj.hoy().minusDays(150);
        Variante filtro = repuesto(10_000, 5, 30_000);

        venta(dia, LocalTime.of(19, 30), null, List.of(new ComandoCobrarVenta.Pago(FormaPago.EFECTIVO, 10_000, null)),
                renglon(filtro, 1));
        venta(dia.plusDays(1), LocalTime.MIDNIGHT, null,
                List.of(new ComandoCobrarVenta.Pago(FormaPago.EFECTIVO, 20_000, null)), renglon(filtro, 2));

        assertThat(resultados(dia, dia).cifras().ventasNetas()).isEqualTo(Dinero.de(10_000));
        assertThat(resultados(dia.plusDays(1), dia.plusDays(1)).cifras().ventasNetas()).isEqualTo(Dinero.de(20_000));
    }

    @Test
    @DisplayName("RF-007: un renglón cuya salida no tuvo costo se cuenta sin costo, no como $0")
    void sinCosto() {
        LocalDate dia = reloj.hoy().minusDays(60);
        Variante filtro = repuesto(10_000, 5, 30_000);
        Venta venta = enEfectivo(dia, 30_000, renglon(filtro, 3));
        jdbc.update("""
                update movimiento_kardex set costo_unitario = null, costo_total = null
                where id = (select movimiento_salida_id from linea_venta where venta_id = ?)
                """, venta.getId());

        ResultadosDelPeriodo r = resultados(dia, dia);

        assertThat(r.cifras().costoVendido()).isEqualTo(Dinero.CERO);
        assertThat(r.sinCosto().renglones()).isEqualTo(1);
        assertThat(r.sinCosto().unidades()).isEqualTo(3);
        assertThat(r.sinCosto().repuestos()).singleElement().satisfies(s -> {
            assertThat(s.varianteId()).isEqualTo(filtro.getId());
            assertThat(s.codigo()).isEqualTo(filtro.getCodigo());
            assertThat(s.vendido()).isEqualTo(Dinero.de(30_000));
        });
    }

    @Test
    @DisplayName("RF-010a: el arriendo del mes repartido carga su cuota un día; solo en el mes, queda fuera del día y entra entero en el mes")
    void gastoDelMes() {
        YearMonth mes = YearMonth.from(reloj.hoy()).minusMonths(8);
        LocalDate primero = mes.atDay(1);
        long cuotaDelDia2 = RepartoEsperado.cuota(800_000, mes);
        registrarGasto.ejecutar(porFuera("Arriendo", NaturalezaGasto.GASTO, 800_000, primero).comoDelMes());

        ResultadosDelPeriodo repartido = resultados(primero.plusDays(1), primero.plusDays(1));
        assertThat(repartido.cifras().gastos()).as("aunque su fecha sea el día anterior").isEqualTo(Dinero.de(cuotaDelDia2));

        ResultadosDelPeriodo delDia = consultarResultados.ejecutar(primero, primero, ModoGastosDelMes.SOLO_EN_EL_MES, personas.administrador())
                .resultados();
        assertThat(delDia.cifras().gastos()).isEqualTo(Dinero.CERO);
        assertThat(delDia.gastosDelMes().fuera()).isEqualTo(Dinero.de(800_000));

        ResultadosDelPeriodo delMes = consultarResultados.ejecutar(primero, mes.atEndOfMonth(),
                ModoGastosDelMes.SOLO_EN_EL_MES, personas.administrador()).resultados();
        assertThat(delMes.cifras().gastos()).isEqualTo(Dinero.de(800_000));
        assertThat(delMes.filaGastosDelMes().gastos()).isEqualTo(Dinero.de(800_000));
        lasFilasSuman(delMes);

        assertThat(resultados(primero, mes.atEndOfMonth()).cifras().gastos()).isEqualTo(Dinero.de(800_000));
    }

    @Test
    @DisplayName("RF-019 y RF-021: repuestos y categorías con sus nombres de la base, el descuento repartido, Sin categoría, y suman las ventas netas")
    void repuestosYCategorias() {
        LocalDate dia = reloj.hoy().minusDays(45);
        List<Categoria> activas = categorias.activas();
        Variante filtro = repuesto(24_000, 5, 50_000, activas.get(0).getId());     // costo $10.000 c/u
        Variante pastillas = repuesto(12_000, 5, 30_000, activas.get(1).getId());  // costo $6.000 c/u
        Variante tornillo = repuesto(1_000, 10, 5_000, activas.get(0).getId());   // costo $500 c/u
        jdbc.update("update producto set categoria_id = null where id = (select producto_id from variante where id = ?)",
                tornillo.getId());

        venta(dia, LocalTime.NOON,
                new ComandoCobrarVenta.ComandoDescuento(ModoDescuento.MONTO, new BigDecimal("3600"), "cliente frecuente"),
                List.of(new ComandoCobrarVenta.Pago(FormaPago.EFECTIVO, 32_400, null)),
                renglon(filtro, 1), renglon(pastillas, 1));
        enEfectivo(dia, 3_000, renglon(tornillo, 3));

        ResultadosDelPeriodo r = resultados(dia, dia);

        assertThat(r.repuestos()).extracting(Vendidos::id, Vendidos::codigo, Vendidos::ventasNetas, Vendidos::utilidad)
                .containsExactly(
                        tuple(filtro.getId(), filtro.getCodigo(), Dinero.de(21_600), Dinero.de(11_600)),
                        tuple(pastillas.getId(), pastillas.getCodigo(), Dinero.de(10_800), Dinero.de(4_800)),
                        tuple(tornillo.getId(), tornillo.getCodigo(), Dinero.de(3_000), Dinero.de(1_500)));
        assertThat(r.repuestos().getFirst().nombre()).isEqualTo("REPUESTO " + filtro.getCodigo());
        assertThat(r.repuestos().getFirst().marca()).isEqualTo("MARCA");
        assertThat(r.categoriasDeRepuesto()).extracting(Vendidos::id, Vendidos::nombre, Vendidos::ventasNetas)
                .containsExactly(
                        tuple(activas.get(0).getId(), activas.get(0).getNombre(), Dinero.de(21_600)),
                        tuple(activas.get(1).getId(), activas.get(1).getNombre(), Dinero.de(10_800)),
                        tuple(null, ResultadosDelPeriodo.SIN_CATEGORIA, Dinero.de(3_000)));
        assertThat(r.categoriasDeRepuesto().stream().map(Vendidos::ventasNetas).reduce(Dinero.CERO, Dinero::mas))
                .isEqualTo(r.cifras().ventasNetas());
    }

    @Test
    @DisplayName("RF-023: el control trae la venta anulada por su día de cobro y el turno cerrado con su faltante")
    void control() {
        LocalDate dia = reloj.hoy().minusDays(20);
        Variante filtro = repuesto(10_000, 5, 30_000);
        Venta anulada = enEfectivo(dia, 20_000, renglon(filtro, 2));
        anularVenta.ejecutar(anulada.getId(), "Se cobró dos veces", cajero);

        TurnoCaja turno = turnos.abierto().orElseThrow();
        Dinero esperado = consultarTurnos.detalle(turno.getId(), personas.administrador()).orElseThrow().arqueo().esperado();
        cerrarTurno.ejecutar(turno.getId(), esperado.menos(Dinero.de(1_400)), cajero);
        jdbc.update("update turno_caja set abierto_en = ?, cerrado_en = ? where id = ?",
                dia.atTime(8, 0).atZone(Periodo.ZONA).toOffsetDateTime(),
                dia.atTime(19, 0).atZone(Periodo.ZONA).toOffsetDateTime(), turno.getId());

        ReporteDeResultados reporte = consultarResultados.ejecutar(dia, dia, ModoGastosDelMes.REPARTIDOS, personas.administrador());

        assertThat(reporte.resultados().cifras().ventas()).as("la anulada no cuenta").isZero();
        assertThat(reporte.control().ventasAnuladas()).isEqualTo(1);
        assertThat(reporte.control().montoAnuladas()).isEqualTo(Dinero.de(20_000));
        assertThat(reporte.control().turnos()).singleElement().satisfies(t -> {
            assertThat(t.id()).isEqualTo(turno.getId());
            assertThat(t.diferencia()).isEqualTo(Dinero.de(-1_400));
            assertThat(t.esperado()).isEqualTo(esperado);
        });
        assertThat(reporte.control().faltantes()).isEqualTo(Dinero.de(1_400));
        assertThat(reporte.periodoAnterior()).isEqualTo(new Periodo(dia.minusDays(1), dia.minusDays(1)));
    }

    @Test
    @DisplayName("un período hasta mañana no se consulta")
    void hastaManana() {
        assertThatThrownBy(() -> resultados(reloj.hoy(), reloj.hoy().plusDays(1)))
                .isInstanceOf(ReglaDeNegocioException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Rendimiento
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("RENDIMIENTO: un año con 20.000 ventas y 50.000 renglones responde en menos de 2 s, y una semana usa el índice por fecha de cobro")
    void unAnoDeVentas() {
        LocalDate desde = reloj.hoy().minusDays(800);
        LocalDate hasta = desde.plusDays(365);
        Variante a = repuesto(12_000, 1, 7_000);
        Variante b = repuesto(12_000, 1, 7_000);
        Variante c = repuesto(12_000, 1, 7_000);
        UUID turno = turnos.abierto().orElseThrow().getId();
        OffsetDateTime inicio = desde.atTime(8, 0).atZone(Periodo.ZONA).toOffsetDateTime();

        // Ventas pares de 3 renglones y nones de 2, cada 26 minutos: 361 días.
        jdbc.update("""
                insert into venta (id, numero, turno_id, vendido_por_id, cobrada_en, subtotal, descuento_monto, total,
                                   estado, llave_idempotencia)
                select md5('carga-venta-' || n)::uuid, 1000000 + n, ?, ?, ? + n * interval '26 minutes',
                       case when n % 2 = 0 then 36000 else 24000 end, 0,
                       case when n % 2 = 0 then 36000 else 24000 end, 'COBRADA', md5('carga-llave-' || n)::uuid
                from generate_series(1, 20000) n
                """, turno, cajero.id(), inicio);
        jdbc.update("""
                insert into linea_venta (id, venta_id, posicion, variante_id, cantidad, precio_unitario, total,
                                         movimiento_salida_id)
                select md5('carga-linea-' || n || '-' || k)::uuid, md5('carga-venta-' || n)::uuid, k,
                       (array[?::uuid, ?::uuid, ?::uuid])[k + 1], 1, 12000, 12000,
                       md5('carga-kardex-' || n || '-' || k)::uuid
                from generate_series(1, 20000) n, generate_series(0, 2) k
                where k < 2 or n % 2 = 0
                """, a.getId(), b.getId(), c.getId());
        jdbc.update("""
                insert into movimiento_kardex (id, variante_id, tipo, cantidad_delta, costo_unitario, costo_total,
                                               saldo_despues, costo_promedio_despues, origen_tipo, origen_id,
                                               registrado_por_id, creado_en)
                select l.movimiento_salida_id, l.variante_id, 'VENTA', -1, 7000, 7000, 0, 7000, 'VENTA', l.venta_id,
                       ?, now()
                from linea_venta l join venta v on v.id = l.venta_id
                where v.numero > 1000000
                """, cajero.id());
        jdbc.update("""
                insert into pago_venta (id, venta_id, forma, monto)
                select md5('carga-pago-' || n)::uuid, md5('carga-venta-' || n)::uuid,
                       case when n % 3 = 0 then 'TRANSFERENCIA' else 'EFECTIVO' end,
                       case when n % 2 = 0 then 36000 else 24000 end
                from generate_series(1, 20000) n
                """);
        jdbc.execute("analyze venta; analyze linea_venta; analyze movimiento_kardex; analyze pago_venta");

        long t0 = System.nanoTime();
        consultarResultados.ejecutar(desde, hasta, ModoGastosDelMes.REPARTIDOS, personas.administrador());
        long primera = (System.nanoTime() - t0) / 1_000_000;
        t0 = System.nanoTime();
        ResultadosDelPeriodo r = consultarResultados.ejecutar(desde, hasta, ModoGastosDelMes.REPARTIDOS, personas.administrador()).resultados();
        long segunda = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("REPORTE DE UN AÑO (20.000 ventas, 50.000 renglones): primera %d ms, segunda %d ms%n",
                primera, segunda);

        assertThat(r.cifras().ventas()).isEqualTo(20_000);
        assertThat(r.cifras().unidades()).isEqualTo(50_000);
        assertThat(r.cifras().ventasNetas()).isEqualTo(Dinero.de(600_000_000));
        assertThat(r.cifras().costoVendido()).isEqualTo(Dinero.de(350_000_000));
        assertThat(r.cifras().efectivo().mas(r.cifras().transferencia())).isEqualTo(r.cifras().ventasNetas());
        assertThat(r.agrupacion()).isEqualTo(Agrupacion.SEMANA);
        lasFilasSuman(r);
        assertThat(primera).as("la primera, en frío").isLessThan(2_000);
        assertThat(segunda).isLessThan(2_000);

        Periodo semana = new Periodo(desde.plusDays(100), desde.plusDays(106));
        String plan = String.join("\n", jdbc.queryForList("""
                explain select v.id from venta v
                where v.estado = 'COBRADA' and v.cobrada_en >= ? and v.cobrada_en < ?
                """, String.class, semana.inicio().atOffset(java.time.ZoneOffset.UTC),
                semana.fin().atOffset(java.time.ZoneOffset.UTC)));
        assertThat(plan).contains("idx_venta_cobrada_en");
    }

    /** La cuota del primer día, calculada aparte del dominio: monto ÷ días, más un peso si sobra. */
    private static final class RepartoEsperado {
        static long cuota(long monto, YearMonth mes) {
            return monto / mes.lengthOfMonth() + (monto % mes.lengthOfMonth() > 0 ? 1 : 0);
        }
    }
}
