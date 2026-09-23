package com.workshopmanagement.rdmotors.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.workshopmanagement.rdmotors.caja.aplicacion.AbrirTurno;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoYaAbiertoException;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.compartido.aplicacion.ActualizarDatosTienda;
import com.workshopmanagement.rdmotors.compartido.aplicacion.ActualizarDatosTienda.ComandoDatosTienda;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.compras.aplicacion.AnularCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.ComandoRegistrarCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarProveedor;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.RenglonesBloqueadosException;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ComandoCrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.aplicacion.CrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.MovimientoKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.TipoMovimiento;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioCategorias;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;
import com.workshopmanagement.rdmotors.ventas.aplicacion.AnularVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.CobrarVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ComandoCobrarVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ConsultarVentas;
import com.workshopmanagement.rdmotors.ventas.aplicacion.DetalleVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ResultadoCobro;
import com.workshopmanagement.rdmotors.ventas.dominio.EstadoVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.ModoDescuento;
import com.workshopmanagement.rdmotors.ventas.dominio.ProblemaDeRenglon;
import com.workshopmanagement.rdmotors.ventas.dominio.RenglonesConProblemaException;

/**
 * Caja y ventas contra un Postgres real (spec 0003).
 *
 * <p>Clase aparte de la de compras a propósito: un solo turno puede estar abierto en toda la base, y
 * compartir contenedor con las pruebas de compras haría que el orden de ejecución decidiera quién
 * encuentra un turno abierto.
 *
 * <p>Las pruebas de turno arrancan sin turno abierto: se cierran por SQL los que hayan quedado, con un arqueo
 * que cuadra al peso con el fondo. Es preparación de la prueba, no una forma de cerrar turnos: el cierre de
 * verdad es {@code CerrarTurno} (spec 0006), probado en {@code CajaIntegracionTest}.
 */
@SpringBootTest
@Import(UsuariosDePrueba.class)
@Testcontainers
class CajaYVentasIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired UsuariosDePrueba personas;
    @Autowired AbrirTurno abrirTurno;
    @Autowired RepositorioTurnos turnos;
    @Autowired JdbcTemplate jdbc;
    @Autowired CobrarVenta cobrarVenta;
    @Autowired ConsultarVentas consultarVentas;
    @Autowired CrearRepuesto crearRepuesto;
    @Autowired RegistrarCompra registrarCompra;
    @Autowired RegistrarProveedor registrarProveedor;
    @Autowired RepositorioVariantes variantes;
    @Autowired RepositorioKardex kardex;
    @Autowired RepositorioAuditoria auditoria;
    @Autowired RepositorioCategorias categorias;
    @Autowired ActualizarDatosTienda actualizarDatosTienda;
    @Autowired AnularVenta anularVenta;
    @Autowired AnularCompra anularCompra;

    private final Actor cajero;

    /** Por el constructor y no en un {@code @BeforeEach}: así ya existe cuando corre cualquiera de ellos. */
    CajaYVentasIntegracionTest(@Autowired UsuariosDePrueba personas) {
        this.cajero = personas.cajero();
    }

    @BeforeEach
    void cerrarTurnosAbiertos() {
        jdbc.update("""
                update turno_caja set estado = 'CERRADO', cerrado_en = now(), cerrado_por_id = abierto_por_id,
                    ventas_efectivo = 0, ventas_transferencia = 0, ventas_fiado = 0, descuentos = 0,
                    devoluciones_efectivo = 0, abonos_efectivo = 0, abonos_transferencia = 0,
                    gastos_cajon = 0, retiros = 0, compras_cajon = 0, esperado = fondo, contado = fondo, diferencia = 0
                where estado = 'ABIERTO'
                """);
    }

    private int turnosAbiertos() {
        return jdbc.queryForObject("select count(*) from turno_caja where estado = 'ABIERTO'", Integer.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SPEC 0003 · FASE 1 — turno de caja
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("abrir un turno contra Postgres: se lee de vuelta con su fondo, y un segundo se rechaza")
    void abrirTurnoContraPostgres() {
        TurnoCaja abierto = abrirTurno.ejecutar(Dinero.de(100_000), cajero);

        assertThat(turnos.abierto()).hasValueSatisfying(t -> {
            assertThat(t.getId()).isEqualTo(abierto.getId());
            assertThat(t.getFondo()).isEqualTo(Dinero.de(100_000));
            assertThat(t.getAbiertoPorId()).isEqualTo(cajero.id());
        });
        assertThatThrownBy(() -> abrirTurno.ejecutar(Dinero.de(50_000), personas.otroCajero()))
                .isInstanceOfSatisfying(TurnoYaAbiertoException.class,
                        e -> assertThat(e.getAbiertoPorId()).isEqualTo(cajero.id()));
        assertThat(turnosAbiertos()).isEqualTo(1);
    }

    @Test
    @DisplayName("LA BASE no deja dos turnos abiertos, aunque se salte el caso de uso")
    void laBaseRechazaDosTurnosAbiertos() {
        abrirTurno.ejecutar(Dinero.de(100_000), personas.otroCajero());

        assertThatThrownBy(() -> jdbc.update("""
                insert into turno_caja (id, abierto_por_id, abierto_en, fondo, estado)
                values (?, ?, now(), 0, 'ABIERTO')
                """, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_turno_abierto");
    }

    @Test
    @DisplayName("la base exige cuándo y quién en un turno cerrado")
    void laBaseExigeDatosDeCierre() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into turno_caja (id, abierto_por_id, abierto_en, fondo, estado)
                values (?, ?, now(), 0, 'CERRADO')
                """, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_turno_datos_de_cierre");
    }

    @Test
    @DisplayName("DOS APERTURAS A LA VEZ dejan un solo turno abierto, y la otra recibe el mismo rechazo")
    void dosAperturasSimultaneas() throws Exception {
        int intentos = 4;
        ExecutorService hilos = Executors.newFixedThreadPool(intentos);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<TurnoCaja>> resultados = new ArrayList<>();
            for (int i = 0; i < intentos; i++) {
                Callable<TurnoCaja> abrir = () -> {
                    largada.await();
                    return abrirTurno.ejecutar(Dinero.de(100_000), personas.otroCajero());
                };
                resultados.add(hilos.submit(abrir));
            }
            largada.countDown();

            int abiertos = 0;
            int rechazados = 0;
            for (Future<TurnoCaja> resultado : resultados) {
                try {
                    resultado.get(30, TimeUnit.SECONDS);
                    abiertos++;
                } catch (java.util.concurrent.ExecutionException e) {
                    // Cualquier otra excepción es un error de verdad: el choque tiene que traducirse.
                    assertThat(e.getCause()).isInstanceOf(TurnoYaAbiertoException.class);
                    rechazados++;
                }
            }

            assertThat(abiertos).isEqualTo(1);
            assertThat(rechazados).isEqualTo(intentos - 1);
            assertThat(turnosAbiertos()).isEqualTo(1);
        } finally {
            hilos.shutdownNow();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SPEC 0003 · FASE 2 — cobrar una venta
    // ─────────────────────────────────────────────────────────────────────────

    /** Un repuesto con stock que entró por una compra de verdad: el kardex queda coherente. */
    private Variante repuestoConStock(int stock, long costoTotal, long precio) {
        String codigo = "VEN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Variante nuevo = crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                "REPUESTO " + codigo, categoriaDePrueba(), null, codigo, "MARCA", Dinero.de(precio), 1), personas.administrador());
        var proveedor = registrarProveedor.ejecutar("Proveedor de pruebas de venta", null, null, personas.administrador());
        registrarCompra.ejecutar(new ComandoRegistrarCompra(proveedor.getId(), LocalDate.of(2026, 9, 14), null,
                FormaPago.EFECTIVO, null, personas.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porTotal(nuevo.getId(), stock, Dinero.de(costoTotal), null))));
        return variantes.buscar(nuevo.getId()).orElseThrow();
    }

    /** Todo repuesto nuevo nace con categoría (spec 0005): cualquiera de las sembradas sirve aquí. */
    private UUID categoriaDePrueba() {
        return categorias.activas().getFirst().getId();
    }

    private static ComandoCobrarVenta.Renglon renglon(Variante v, int cantidad) {
        return new ComandoCobrarVenta.Renglon(v.getId(), cantidad, v.getPrecio().valor().longValueExact());
    }

    private ComandoCobrarVenta enEfectivo(UUID llave, long total, ComandoCobrarVenta.Renglon... renglones) {
        return new ComandoCobrarVenta(llave, List.of(renglones), null,
                List.of(new ComandoCobrarVenta.Pago(FormaPago.EFECTIVO, total, null)), cajero);
    }

    private int stockDe(Variante v) {
        return variantes.buscar(v.getId()).orElseThrow().getStock();
    }

    private void conTurnoAbierto() {
        if (turnos.abierto().isEmpty()) {
            abrirTurno.ejecutar(Dinero.de(100_000), cajero);
        }
    }

    /** Corre las tareas a la vez, largándolas juntas, y devuelve lo que devolvió o lanzó cada una. */
    private static <T> List<Object> aLaVez(List<Callable<T>> tareas) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(tareas.size());
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (Callable<T> tarea : tareas) {
                futuros.add(hilos.submit(() -> {
                    largada.await();
                    return tarea.call();
                }));
            }
            largada.countDown();
            List<Object> resultados = new ArrayList<>();
            for (Future<T> futuro : futuros) {
                try {
                    resultados.add(futuro.get(60, TimeUnit.SECONDS));
                } catch (java.util.concurrent.ExecutionException e) {
                    resultados.add(e.getCause());
                }
            }
            return resultados;
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test
    @DisplayName("cobrar contra Postgres: venta, renglones, pagos y cambio se leen de vuelta; el stock baja; el kardex registra la venta; el descuento queda auditado")
    void cobrarContraPostgres() {
        conTurnoAbierto();
        Variante filtro = repuestoConStock(10, 80_000, 13_000);   // costo $8.000 c/u
        Variante pastillas = repuestoConStock(5, 37_500, 12_000);   // costo $7.500 c/u

        ResultadoCobro resultado = cobrarVenta.ejecutar(new ComandoCobrarVenta(UUID.randomUUID(),
                List.of(renglon(filtro, 2), renglon(pastillas, 1)),
                new ComandoCobrarVenta.ComandoDescuento(ModoDescuento.PORCENTAJE, new BigDecimal("10"), "cliente frecuente"),
                List.of(new ComandoCobrarVenta.Pago(FormaPago.EFECTIVO, 20_000, 50_000L),
                        new ComandoCobrarVenta.Pago(FormaPago.TRANSFERENCIA, 14_200, null)),
                cajero));

        DetalleVenta detalle = consultarVentas.detalle(resultado.venta().getId(), personas.administrador()).orElseThrow();
        assertThat(detalle.subtotal()).isEqualTo(Dinero.de(38_000));
        assertThat(detalle.descuento()).isEqualTo(Dinero.de(3_800));
        assertThat(detalle.descuentoPorcentaje()).isEqualByComparingTo("10");
        assertThat(detalle.total()).isEqualTo(Dinero.de(34_200));
        assertThat(detalle.cambio()).isEqualTo(Dinero.de(30_000));
        assertThat(detalle.renglones()).extracting(DetalleVenta.Renglon::codigo)
                .containsExactly(filtro.getCodigo(), pastillas.getCodigo());
        assertThat(detalle.pagos()).extracting(DetalleVenta.Pago::forma)
                .containsExactlyInAnyOrder(FormaPago.EFECTIVO, FormaPago.TRANSFERENCIA);

        assertThat(stockDe(filtro)).isEqualTo(8);
        assertThat(stockDe(pastillas)).isEqualTo(4);
        MovimientoKardex salida = kardex.historialDe(filtro.getId()).getLast();
        assertThat(salida.getTipo()).isEqualTo(TipoMovimiento.VENTA);
        assertThat(salida.getCostoTotal()).isEqualTo(Dinero.de(16_000));
        assertThat(salida.getCostoPromedioDespues()).isEqualByComparingTo("8000");

        assertThat(auditoria.historialDe("VENTA", detalle.id())).singleElement().satisfies(e -> {
            assertThat(e.accion()).isEqualTo(AccionAuditada.APLICAR_DESCUENTO);
            assertThat(e.motivo()).isEqualTo("cliente frecuente");
        });
        assertThat(consultarVentas.delTurnoAbierto(personas.administrador())).extracting(DetalleVenta::id).contains(detalle.id());
        assertThat(consultarVentas.porNumero(detalle.numero(), personas.administrador())).map(DetalleVenta::id).contains(detalle.id());
    }

    @Test
    @DisplayName("SIN HUECOS: los cobros que fallan no se comen números, ni antes ni después de pedirlo")
    void numeracionSinHuecos() {
        conTurnoAbierto();
        Variante repuesto = repuestoConStock(10, 50_000, 9_000);

        long primero = cobrarVenta.ejecutar(enEfectivo(UUID.randomUUID(), 9_000, renglon(repuesto, 1))).venta().getNumero();
        // Falla ANTES de pedir número: no hay stock para 50.
        assertThatThrownBy(() -> cobrarVenta.ejecutar(enEfectivo(UUID.randomUUID(), 450_000, renglon(repuesto, 50))))
                .isInstanceOf(RenglonesConProblemaException.class);
        // Falla DESPUÉS de pedir número: los pagos no cuadran. El número tiene que volver con el rollback.
        assertThatThrownBy(() -> cobrarVenta.ejecutar(enEfectivo(UUID.randomUUID(), 1_000, renglon(repuesto, 1))))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("faltan");
        long segundo = cobrarVenta.ejecutar(enEfectivo(UUID.randomUUID(), 9_000, renglon(repuesto, 1))).venta().getNumero();

        assertThat(segundo).isEqualTo(primero + 1);
        assertThat(stockDe(repuesto)).isEqualTo(8);
    }

    @Test
    @DisplayName("LA ÚLTIMA UNIDAD: dos cobros a la vez por ella dejan una sola venta; el otro dice que ya no quedan")
    void ultimaUnidad() throws Exception {
        conTurnoAbierto();
        Variante repuesto = repuestoConStock(1, 9_000, 15_000);

        List<Object> resultados = aLaVez(List.<Callable<ResultadoCobro>>of(
                () -> cobrarVenta.ejecutar(enEfectivo(UUID.randomUUID(), 15_000, renglon(repuesto, 1))),
                () -> cobrarVenta.ejecutar(enEfectivo(UUID.randomUUID(), 15_000, renglon(repuesto, 1)))));

        assertThat(resultados).filteredOn(ResultadoCobro.class::isInstance).hasSize(1);
        assertThat(resultados).filteredOn(RenglonesConProblemaException.class::isInstance).singleElement()
                .satisfies(e -> assertThat(((RenglonesConProblemaException) e).getProblemas())
                        .extracting(ProblemaDeRenglon::tipo).containsExactly(ProblemaDeRenglon.Tipo.SIN_STOCK));
        assertThat(stockDe(repuesto)).isZero();
    }

    @Test
    @DisplayName("ORDEN DE BLOQUEO: ventas con los mismos repuestos en orden A,B y B,A a la vez terminan todas, sin trabarse")
    void sinTrabasPorOrden() throws Exception {
        conTurnoAbierto();
        Variante a = repuestoConStock(50, 100_000, 5_000);
        Variante b = repuestoConStock(50, 100_000, 7_000);
        int ventasPorOrden = 4;

        List<Callable<ResultadoCobro>> tareas = new ArrayList<>();
        for (int i = 0; i < ventasPorOrden; i++) {
            tareas.add(() -> cobrarVenta.ejecutar(enEfectivo(UUID.randomUUID(), 12_000, renglon(a, 1), renglon(b, 1))));
            tareas.add(() -> cobrarVenta.ejecutar(enEfectivo(UUID.randomUUID(), 12_000, renglon(b, 1), renglon(a, 1))));
        }
        List<Object> resultados = aLaVez(tareas);

        // Una traba la detecta Postgres y aborta una de las dos: aparecería aquí como una excepción.
        assertThat(resultados).allMatch(ResultadoCobro.class::isInstance);
        assertThat(stockDe(a)).isEqualTo(50 - 2 * ventasPorOrden);
        assertThat(stockDe(b)).isEqualTo(50 - 2 * ventasPorOrden);
    }

    @Test
    @DisplayName("LA MISMA LLAVE A LA VEZ, por la última unidad: una sola venta, y los dos reciben esa venta en vez de sin stock")
    void mismaLlaveALaVez() throws Exception {
        conTurnoAbierto();
        Variante repuesto = repuestoConStock(1, 9_000, 15_000);
        ComandoCobrarVenta dobleClic = enEfectivo(UUID.randomUUID(), 15_000, renglon(repuesto, 1));

        List<Object> resultados = aLaVez(List.<Callable<ResultadoCobro>>of(
                () -> cobrarVenta.ejecutar(dobleClic),
                () -> cobrarVenta.ejecutar(dobleClic)));

        assertThat(resultados).allMatch(ResultadoCobro.class::isInstance);
        UUID laVenta = ((ResultadoCobro) resultados.getFirst()).venta().getId();
        assertThat(resultados).extracting(r -> ((ResultadoCobro) r).venta().getId()).containsOnly(laVenta);
        assertThat(resultados).extracting(r -> ((ResultadoCobro) r).repetida()).containsExactlyInAnyOrder(true, false);
        assertThat(stockDe(repuesto)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from venta where llave_idempotencia = ?", Integer.class,
                dobleClic.llave())).isEqualTo(1);
    }

    @Test
    @DisplayName("ATOMICIDAD: si la base rechaza la venta al guardarla, no queda ni el stock descontado, ni el kardex, ni el número")
    void atomicidad() {
        conTurnoAbierto();
        Variante repuesto = repuestoConStock(10, 50_000, 9_000);
        long numero = cobrarVenta.ejecutar(enEfectivo(UUID.randomUUID(), 9_000, renglon(repuesto, 1))).venta().getNumero();
        int movimientosAntes = kardex.historialDe(repuesto.getId()).size();

        // El contador se atrasa a propósito: el próximo cobro repite un número que ya existe, y la base
        // lo rechaza en el último paso, cuando el stock y el kardex ya se movieron.
        jdbc.update("update consecutivo set ultimo = ultimo - 1 where nombre = 'VENTA'");
        try {
            assertThatThrownBy(() -> cobrarVenta.ejecutar(enEfectivo(UUID.randomUUID(), 18_000, renglon(repuesto, 2))))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ux_venta_numero");
        } finally {
            jdbc.update("update consecutivo set ultimo = ultimo + 1 where nombre = 'VENTA'");
        }

        assertThat(stockDe(repuesto)).isEqualTo(9);
        assertThat(kardex.historialDe(repuesto.getId())).hasSize(movimientosAntes);
        assertThat(jdbc.queryForObject("select ultimo from consecutivo where nombre = 'VENTA'", Long.class))
                .isEqualTo(numero);
    }

    @Test
    @DisplayName("la base exige que las partes sumen, que el descuento tenga motivo y que lo recibido alcance")
    void laBaseExigeLasReglasDeLaVenta() {
        conTurnoAbierto();
        UUID turno = turnos.abierto().orElseThrow().getId();
        String venta = """
                insert into venta (id, numero, turno_id, vendido_por_id, cobrada_en, subtotal, descuento_monto,
                                   descuento_modo, descuento_motivo, total, estado, llave_idempotencia)
                values (?, ?, ?, ?, now(), ?, ?, ?, ?, ?, 'COBRADA', ?)
                """;
        long numeroLibre = 900_000_000L + Math.abs(System.nanoTime() % 1_000_000);

        assertThatThrownBy(() -> jdbc.update(venta, UUID.randomUUID(), numeroLibre, turno, cajero.id(),
                38_000, 0, null, null, 30_000, UUID.randomUUID()))
                .hasMessageContaining("ck_venta_total");
        assertThatThrownBy(() -> jdbc.update(venta, UUID.randomUUID(), numeroLibre + 1, turno, cajero.id(),
                38_000, 3_000, "MONTO", null, 35_000, UUID.randomUUID()))
                .hasMessageContaining("ck_venta_descuento");

        UUID ventaValida = UUID.randomUUID();
        jdbc.update(venta, ventaValida, numeroLibre + 2, turno, cajero.id(), 10_000, 0, null, null, 10_000, UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update(
                "insert into pago_venta (id, venta_id, forma, monto, recibido) values (?, ?, 'EFECTIVO', 10000, 5000)",
                UUID.randomUUID(), ventaValida))
                .hasMessageContaining("ck_pago_venta_recibido");
        jdbc.update("delete from venta where id = ?", ventaValida);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SPEC 0003 · FASE 5 — anular una venta
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("anular contra Postgres: el stock vuelve, la reversión apunta a su salida, queda el evento, y la siguiente venta sigue la serie")
    void anularContraPostgres() {
        conTurnoAbierto();
        Variante filtro = repuestoConStock(10, 80_000, 13_000);   // costo $8.000 c/u
        ResultadoCobro cobrada = cobrarVenta.ejecutar(enEfectivo(UUID.randomUUID(), 26_000, renglon(filtro, 2)));
        UUID ventaId = cobrada.venta().getId();
        long numero = cobrada.venta().getNumero();
        MovimientoKardex salida = kardex.historialDe(filtro.getId()).getLast();
        assertThat(stockDe(filtro)).isEqualTo(8);

        anularVenta.ejecutar(ventaId, "Cliente se arrepintió", cajero);

        assertThat(stockDe(filtro)).isEqualTo(10);
        assertThat(variantes.buscar(filtro.getId()).orElseThrow().getCostoPromedio()).isEqualByComparingTo("8000");
        MovimientoKardex reversion = kardex.historialDe(filtro.getId()).getLast();
        assertThat(reversion.getTipo()).isEqualTo(TipoMovimiento.REVERSION);
        assertThat(reversion.getMovimientoRevertidoId()).isEqualTo(salida.getId());
        assertThat(reversion.getCantidadDelta()).isEqualTo(2);
        assertThat(reversion.getCostoTotal()).isEqualTo(Dinero.de(16_000));
        assertThat(reversion.getSecuencia()).isGreaterThan(salida.getSecuencia());

        DetalleVenta anulada = consultarVentas.detalle(ventaId, personas.administrador()).orElseThrow();
        assertThat(anulada.estado()).isEqualTo(EstadoVenta.ANULADA);
        assertThat(anulada.motivoAnulacion()).isEqualTo("Cliente se arrepintió");
        assertThat(jdbc.queryForObject("select anulada_en_turno_id from venta where id = ?", UUID.class, ventaId))
                .isEqualTo(turnos.abierto().orElseThrow().getId());
        assertThat(auditoria.historialDe("VENTA", ventaId))
                .anySatisfy(e -> assertThat(e.accion()).isEqualTo(AccionAuditada.ANULAR_VENTA));

        // El número de la anulada no se reutiliza: la siguiente sigue la serie.
        ResultadoCobro siguiente = cobrarVenta.ejecutar(enEfectivo(UUID.randomUUID(), 13_000, renglon(filtro, 1)));
        assertThat(siguiente.venta().getNumero()).isEqualTo(numero + 1);
    }

    @Test
    @DisplayName("DOS ANULACIONES A LA VEZ de la misma venta: una anula, la otra dice que ya fue anulada, y el stock vuelve una sola vez")
    void dosAnulacionesALaVez() throws Exception {
        conTurnoAbierto();
        Variante filtro = repuestoConStock(10, 80_000, 13_000);
        UUID ventaId = cobrarVenta.ejecutar(enEfectivo(UUID.randomUUID(), 39_000, renglon(filtro, 3))).venta().getId();

        List<Object> resultados = aLaVez(List.<Callable<Object>>of(
                () -> anularVenta.ejecutar(ventaId, "doble clic", cajero),
                () -> anularVenta.ejecutar(ventaId, "doble clic", cajero)));

        assertThat(resultados).filteredOn(r -> r instanceof Throwable).singleElement()
                .satisfies(e -> assertThat((Throwable) e).isInstanceOf(ReglaDeNegocioException.class)
                        .hasMessage("Esta venta ya fue anulada"));
        assertThat(stockDe(filtro)).isEqualTo(10);
        assertThat(kardex.historialDe(filtro.getId())).filteredOn(m -> m.getTipo() == TipoMovimiento.REVERSION).hasSize(1);
    }

    @Test
    @DisplayName("RF-030 contra Postgres: la compra de un repuesto vendido no se anula; anulada la venta, sí")
    void ventaAnuladaNoBloqueaAnularLaCompra() {
        conTurnoAbierto();
        String codigo = "RF30-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Variante nuevo = crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                "REPUESTO " + codigo, categoriaDePrueba(), null, codigo, "MARCA", Dinero.de(13_000), 1), personas.administrador());
        var proveedor = registrarProveedor.ejecutar("Proveedor de pruebas RF-030", null, null, personas.administrador());
        Compra compra = registrarCompra.ejecutar(new ComandoRegistrarCompra(proveedor.getId(), LocalDate.of(2026, 9, 14),
                null, FormaPago.EFECTIVO, null, personas.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porTotal(nuevo.getId(), 10, Dinero.de(80_000), null))));
        Variante filtro = variantes.buscar(nuevo.getId()).orElseThrow();
        UUID ventaId = cobrarVenta.ejecutar(enEfectivo(UUID.randomUUID(), 26_000, renglon(filtro, 2))).venta().getId();

        assertThatThrownBy(() -> anularCompra.ejecutar(compra.getId(), 0, "se registró dos veces", personas.administrador()))
                .isInstanceOf(RenglonesBloqueadosException.class);

        anularVenta.ejecutar(ventaId, "Cliente se arrepintió", cajero);
        anularCompra.ejecutar(compra.getId(), 0, "se registró dos veces", personas.administrador());

        assertThat(stockDe(filtro)).isZero();
    }

    @Test
    @DisplayName("RF-030 contra Postgres: si una compra movió el promedio entre la venta y su anulación, la compra anterior sigue bloqueada")
    void ventaAnuladaConCompraEnElMedioSigueBloqueando() {
        conTurnoAbierto();
        String codigo = "RF30B-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Variante nuevo = crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                "REPUESTO " + codigo, categoriaDePrueba(), null, codigo, "MARCA", Dinero.de(13_000), 1), personas.administrador());
        var proveedor = registrarProveedor.ejecutar("Proveedor de pruebas RF-030", null, null, personas.administrador());
        Compra primera = registrarCompra.ejecutar(new ComandoRegistrarCompra(proveedor.getId(), LocalDate.of(2026, 9, 14),
                null, FormaPago.EFECTIVO, null, personas.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porTotal(nuevo.getId(), 10, Dinero.de(10_000), null))));
        Variante filtro = variantes.buscar(nuevo.getId()).orElseThrow();
        UUID ventaId = cobrarVenta.ejecutar(enEfectivo(UUID.randomUUID(), 39_000, renglon(filtro, 3))).venta().getId();
        registrarCompra.ejecutar(new ComandoRegistrarCompra(proveedor.getId(), LocalDate.of(2026, 9, 14),
                null, FormaPago.EFECTIVO, null, personas.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porTotal(nuevo.getId(), 10, Dinero.de(20_000), null))));
        anularVenta.ejecutar(ventaId, "Cliente se arrepintió", cajero);

        assertThatThrownBy(() -> anularCompra.ejecutar(primera.getId(), 0, "se registró dos veces", personas.administrador()))
                .isInstanceOf(RenglonesBloqueadosException.class);
    }

    @Test
    @DisplayName("la base exige que un movimiento se revierta una sola vez")
    void laBaseExigeUnaReversionPorMovimiento() {
        conTurnoAbierto();
        Variante filtro = repuestoConStock(10, 80_000, 13_000);
        UUID ventaId = cobrarVenta.ejecutar(enEfectivo(UUID.randomUUID(), 13_000, renglon(filtro, 1))).venta().getId();
        anularVenta.ejecutar(ventaId, "prueba", cajero);
        MovimientoKardex reversion = kardex.historialDe(filtro.getId()).getLast();

        assertThatThrownBy(() -> jdbc.update("""
                insert into movimiento_kardex (id, variante_id, tipo, cantidad_delta, costo_unitario, costo_total,
                    saldo_despues, costo_promedio_despues, origen_tipo, origen_id, movimiento_revertido_id,
                    registrado_por_id, creado_en)
                values (?, ?, 'REVERSION', 1, 8000, 8000, 11, 8000, 'VENTA', ?, ?, ?, now())
                """, UUID.randomUUID(), filtro.getId(), ventaId, reversion.getMovimientoRevertidoId(), cajero.id()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_kardex_revertido");
    }

    // ── Datos de la tienda (fase 4, RF-033) ─────────────────────────────────────

    /**
     * Es la única prueba que cambia la fila de la tienda: por eso puede mirar primero la siembra. Las de
     * los CHECK fallan, y lo que falla no queda.
     */
    @Test
    @DisplayName("la tienda arranca con RD MOTORS, y reemplazar dos veces lo mismo deja una sola fila igual")
    void datosDeLaTiendaContraPostgres() {
        assertThat(jdbc.queryForObject("select nombre_comercial from datos_tienda where id = 1", String.class))
                .isEqualTo("RD MOTORS");

        ComandoDatosTienda comando = new ComandoDatosTienda("RD Motors Almacén", "900.123.456-7",
                "Calle 10 # 5-20", "300 123 4567", "Gracias por su compra");
        actualizarDatosTienda.ejecutar(comando, personas.administrador());
        actualizarDatosTienda.ejecutar(comando, personas.administrador());

        assertThat(jdbc.queryForObject("select count(*) from datos_tienda", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForMap("select nombre_comercial, nit, direccion, telefono, mensaje_pie from datos_tienda"))
                .containsEntry("nombre_comercial", "RD Motors Almacén")
                .containsEntry("nit", "900.123.456-7")
                .containsEntry("direccion", "Calle 10 # 5-20")
                .containsEntry("telefono", "300 123 4567")
                .containsEntry("mensaje_pie", "Gracias por su compra");

        // Borrar un opcional desde la pantalla lo deja en NULL, no en texto vacío.
        actualizarDatosTienda.ejecutar(new ComandoDatosTienda("RD Motors Almacén", "", null, null, null), personas.administrador());
        assertThat(jdbc.queryForObject("select nit is null and mensaje_pie is null from datos_tienda", Boolean.class))
                .isTrue();
    }

    @Test
    @DisplayName("la base exige una sola fila de la tienda, con nombre, y sin opcionales en blanco")
    void laBaseExigeUnaSolaTienda() {
        assertThatThrownBy(() -> jdbc.update("insert into datos_tienda (id, nombre_comercial) values (2, 'Otra tienda')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("datos_tienda_id_check");
        assertThatThrownBy(() -> jdbc.update("update datos_tienda set nombre_comercial = '   '"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("datos_tienda_nombre_comercial_check");
        assertThatThrownBy(() -> jdbc.update("update datos_tienda set nit = ''"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("datos_tienda_nit_check");
    }
}

