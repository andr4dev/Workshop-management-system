package com.workshopmanagement.rdmotors.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import com.workshopmanagement.rdmotors.caja.aplicacion.AnularGasto;
import com.workshopmanagement.rdmotors.caja.aplicacion.CerrarTurno;
import com.workshopmanagement.rdmotors.caja.aplicacion.ComandoRegistrarGasto;
import com.workshopmanagement.rdmotors.caja.aplicacion.ConsultarGastos;
import com.workshopmanagement.rdmotors.caja.aplicacion.ConsultarTurnos;
import com.workshopmanagement.rdmotors.caja.aplicacion.DetalleGasto;
import com.workshopmanagement.rdmotors.caja.aplicacion.DetalleTurno;
import com.workshopmanagement.rdmotors.caja.aplicacion.EscribirObservaciones;
import com.workshopmanagement.rdmotors.caja.aplicacion.RegistrarCategoriaGasto;
import com.workshopmanagement.rdmotors.caja.aplicacion.RegistrarGasto;
import com.workshopmanagement.rdmotors.caja.aplicacion.RegistrarRetiro;
import com.workshopmanagement.rdmotors.caja.dominio.CategoriaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.FiltroGastos;
import com.workshopmanagement.rdmotors.caja.dominio.Gasto;
import com.workshopmanagement.rdmotors.caja.dominio.MovimientoRepetidoException;
import com.workshopmanagement.rdmotors.caja.dominio.NaturalezaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.SinTurnoAbiertoException;
import com.workshopmanagement.rdmotors.caja.dominio.TotalesGastos;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoYaCerradoException;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioCategoriasGasto;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.compras.aplicacion.ComandoRegistrarCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarCuenta;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarProveedor;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;
import com.workshopmanagement.rdmotors.compras.dominio.Proveedor;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ComandoCrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.aplicacion.CrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioCategorias;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;
import com.workshopmanagement.rdmotors.ventas.aplicacion.AnularVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.CobrarVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ComandoCobrarVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ResultadoCobro;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;

/**
 * Caja contra un Postgres real (spec 0006): gastos, retiros, compras de caja y el cierre.
 *
 * <p>La que más protege es {@link #cobrosYCierreALaVez()}: los dobles en memoria no tienen transacciones, así
 * que la única forma de saber que un cobro nunca queda en un turno cerrado sin contar es largarlos juntos
 * contra la base de verdad.
 *
 * <p>Clase y contenedor aparte, como caja y ventas: un solo turno puede estar abierto en toda la base.
 */
@SpringBootTest
@Import(UsuariosDePrueba.class)
@Testcontainers
class CajaIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired UsuariosDePrueba personas;
    @Autowired JdbcTemplate jdbc;
    @Autowired AbrirTurno abrirTurno;
    @Autowired CerrarTurno cerrarTurno;
    @Autowired EscribirObservaciones escribirObservaciones;
    @Autowired ConsultarTurnos consultarTurnos;
    @Autowired RepositorioTurnos turnos;
    @Autowired RegistrarGasto registrarGasto;
    @Autowired AnularGasto anularGasto;
    @Autowired ConsultarGastos consultarGastos;
    @Autowired RegistrarRetiro registrarRetiro;
    @Autowired RegistrarCategoriaGasto registrarCategoria;
    @Autowired RepositorioCategoriasGasto categoriasGasto;
    @Autowired CobrarVenta cobrarVenta;
    @Autowired AnularVenta anularVenta;
    @Autowired RegistrarCompra registrarCompra;
    @Autowired RegistrarProveedor registrarProveedor;
    @Autowired RegistrarCuenta registrarCuenta;
    @Autowired CrearRepuesto crearRepuesto;
    @Autowired RepositorioVariantes variantes;
    @Autowired RepositorioCategorias categorias;
    @Autowired RepositorioAuditoria auditoria;

    private final Actor cajero;

    /** Por el constructor y no en un {@code @BeforeEach}: así ya existe cuando corre cualquiera de ellos. */
    CajaIntegracionTest(@Autowired UsuariosDePrueba personas) {
        this.cajero = personas.cajero();
    }

    /** Arranca sin turno abierto: los que hayan quedado se cierran por SQL, con un arqueo que cuadra. */
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

    // ── Atajos ───────────────────────────────────────────────────────────────

    private String codigo(String prefijo) {
        return prefijo + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Proveedor proveedor() {
        return registrarProveedor.ejecutar("Importadora Jotapartes " + UUID.randomUUID(), null, null, personas.administrador());
    }

    /** Un repuesto con stock que entró por una compra que NO se pagó con plata del cajón. */
    private Variante repuestoConStock(int stock, long precio) {
        String codigo = codigo("CAJA");
        Variante nuevo = crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo("REPUESTO " + codigo,
                categorias.activas().getFirst().getId(), null, codigo, "MARCA", Dinero.de(precio), 1), personas.administrador());
        registrarCompra.ejecutar(new ComandoRegistrarCompra(proveedor().getId(), LocalDate.of(2026, 9, 16), null,
                FormaPago.EFECTIVO, null, personas.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porTotal(nuevo.getId(), stock, Dinero.de(stock * 100L), null))));
        return variantes.buscar(nuevo.getId()).orElseThrow();
    }

    private ComandoCobrarVenta cobro(Variante repuesto, long efectivo, long transferencia) {
        List<ComandoCobrarVenta.Pago> pagos = new ArrayList<>();
        if (efectivo > 0) pagos.add(new ComandoCobrarVenta.Pago(FormaPago.EFECTIVO, efectivo, null));
        if (transferencia > 0) pagos.add(new ComandoCobrarVenta.Pago(FormaPago.TRANSFERENCIA, transferencia, null));
        return new ComandoCobrarVenta(UUID.randomUUID(),
                List.of(new ComandoCobrarVenta.Renglon(repuesto.getId(), 1, efectivo + transferencia)), null, pagos,
                cajero);
    }

    private Venta vender(long efectivo, long transferencia) {
        return cobrarVenta.ejecutar(cobro(repuestoConStock(5, efectivo + transferencia), efectivo, transferencia))
                .venta();
    }

    private UUID categoria(String nombre) {
        return categoriasGasto.buscarPorNombre(nombre).orElseThrow().getId();
    }

    private Compra compraDeCaja(long total) {
        Variante repuesto = repuestoConStock(1, total * 2);
        return registrarCompra.ejecutar(new ComandoRegistrarCompra(proveedor().getId(), LocalDate.of(2026, 9, 16),
                "FV-CAJA", FormaPago.EFECTIVO, null, personas.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porTotal(repuesto.getId(), 1, Dinero.de(total), null)), true));
    }

    private BigDecimal columna(String columna, UUID turnoId) {
        return jdbc.queryForObject("select " + columna + " from turno_caja where id = ?", BigDecimal.class, turnoId);
    }

    /** Corre las tareas a la vez, largándolas juntas, y devuelve lo que devolvió o lanzó cada una, en orden. */
    private static List<Object> aLaVez(List<Callable<Object>> tareas) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(tareas.size());
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Object>> futuros = new ArrayList<>();
            for (Callable<Object> tarea : tareas) {
                futuros.add(hilos.submit(() -> {
                    largada.await();
                    return tarea.call();
                }));
            }
            largada.countDown();
            List<Object> resultados = new ArrayList<>();
            for (Future<Object> futuro : futuros) {
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

    // ─────────────────────────────────────────────────────────────────────────
    //  FASE 1 — categorías y gastos
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("la base arranca con las 11 categorías del spec, todas gasto")
    void siembra() {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "select nombre, naturaleza from categoria_gasto where nombre in (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "Arriendo", "Servicios públicos", "Internet y teléfono", "Nómina", "Transporte y fletes",
                "Alimentación", "Aseo y cafetería", "Papelería", "Mantenimiento", "Impuestos y trámites", "Otros");

        assertThat(filas).hasSize(11).allSatisfy(f -> assertThat(f.get("naturaleza")).isEqualTo("GASTO"));
    }

    @Test
    @DisplayName("LA BASE no deja dos categorías con el mismo nombre sin mayúsculas ni tildes, y el caso de uso lo dice")
    void categoriaRepetida() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into categoria_gasto (id, nombre, naturaleza) values (gen_random_uuid(), 'PAPELERIA', 'GASTO')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_categoria_gasto_nombre");
        assertThatThrownBy(() -> registrarCategoria.ejecutar("papeleria", NaturalezaGasto.GASTO, personas.administrador()))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage("Ya existe la categoría «Papelería»");

        String nombre = "Publicidad " + UUID.randomUUID().toString().substring(0, 6);
        CategoriaGasto nueva = registrarCategoria.ejecutar(nombre, NaturalezaGasto.COSTO, personas.administrador());
        assertThat(categoriasGasto.buscar(nueva.getId())).hasValueSatisfying(c -> {
            assertThat(c.getNombre()).isEqualTo(nombre);
            assertThat(c.getNaturaleza()).isEqualTo(NaturalezaGasto.COSTO);
        });
    }

    @Test
    @DisplayName("gastos contra Postgres: del cajón y por transferencia se leen de vuelta; la lista filtra y los totales cuadran sin los anulados")
    void gastosContraPostgres() {
        TurnoCaja turno = abrirTurno.ejecutar(Dinero.de(100_000), cajero);
        CuentaPago nequi = registrarCuenta.ejecutar("Nequi " + UUID.randomUUID().toString().substring(0, 6), personas.administrador());
        LocalDate dia = LocalDate.of(2026, 8, 1).plusDays((long) (Math.random() * 20));

        Gasto flete = registrarGasto.ejecutar(ComandoRegistrarGasto.delCajon(UUID.randomUUID(),
                categoria("Transporte y fletes"), Dinero.de(15_000), "Flete Jotapartes FV-9912", cajero));
        Gasto arriendo = registrarGasto.ejecutar(ComandoRegistrarGasto.porFuera(UUID.randomUUID(), categoria("Arriendo"),
                Dinero.de(800_000), "Arriendo de agosto", FormaPago.TRANSFERENCIA, nequi.getId(), dia, personas.administrador()));
        Gasto anulado = registrarGasto.ejecutar(ComandoRegistrarGasto.porFuera(UUID.randomUUID(), categoria("Arriendo"),
                Dinero.de(800_000), "Arriendo repetido", FormaPago.EFECTIVO, null, dia, personas.administrador()));
        anularGasto.ejecutar(anulado.getId(), "Se registró dos veces", personas.administrador());

        assertThat(consultarGastos.detalle(flete.getId(), personas.administrador())).hasValueSatisfying(g -> {
            assertThat(g.monto()).isEqualTo(Dinero.de(15_000));
            assertThat(g.delCajon()).isTrue();
            assertThat(g.turnoId()).isEqualTo(turno.getId());
            assertThat(g.categoria()).isEqualTo("Transporte y fletes");
            assertThat(g.naturaleza()).isEqualTo(NaturalezaGasto.GASTO);
        });
        assertThat(consultarGastos.detalle(arriendo.getId(), personas.administrador())).hasValueSatisfying(g -> {
            assertThat(g.cuenta()).isEqualTo(nequi.getNombre());
            assertThat(g.fecha()).isEqualTo(dia);
            assertThat(g.turnoId()).isNull();
        });

        FiltroGastos delDia = new FiltroGastos(dia, dia, categoria("Arriendo"));
        assertThat(consultarGastos.listar(delDia, 0, 25, personas.administrador()).elementos()).extracting(DetalleGasto::id)
                .contains(arriendo.getId(), anulado.getId()).doesNotContain(flete.getId());
        TotalesGastos totales = consultarGastos.totales(delDia, personas.administrador());
        assertThat(totales.delCajon().mas(totales.porFuera())).isEqualTo(totales.total());
        assertThat(consultarGastos.listar(delDia, 0, 25, personas.administrador()).elementos().stream()
                .filter(g -> g.anuladoEn() == null).map(DetalleGasto::monto).reduce(Dinero.CERO, Dinero::mas))
                .isEqualTo(totales.total());
        assertThat(auditoria.historialDe(Gasto.TIPO_AUDITORIA, anulado.getId()))
                .singleElement().satisfies(e -> assertThat(e.accion()).isEqualTo(AccionAuditada.ANULAR_GASTO));
    }

    @Test
    @DisplayName("LA MISMA LLAVE A LA VEZ deja un solo gasto")
    void mismaLlaveALaVez() throws Exception {
        abrirTurno.ejecutar(Dinero.de(100_000), cajero);
        ComandoRegistrarGasto dobleClic = ComandoRegistrarGasto.delCajon(UUID.randomUUID(),
                categoria("Alimentación"), Dinero.de(12_000), "Almuerzo", cajero);

        List<Object> resultados = aLaVez(List.of(() -> registrarGasto.ejecutar(dobleClic),
                () -> registrarGasto.ejecutar(dobleClic)));

        assertThat(resultados).allMatch(r -> r instanceof Gasto || r instanceof MovimientoRepetidoException);
        assertThat(jdbc.queryForObject("select count(*) from gasto where llave_idempotencia = ?", Integer.class,
                dobleClic.llave())).isEqualTo(1);
    }

    @Test
    @DisplayName("la base exige: un gasto del cajón con turno y en efectivo, la transferencia con cuenta, y la anulación completa")
    void laBaseExigeLasReglasDelGasto() {
        TurnoCaja turno = abrirTurno.ejecutar(Dinero.de(0), cajero);
        UUID categoria = categoria("Otros");
        String insert = """
                insert into gasto (id, categoria_id, monto, descripcion, del_cajon, turno_id, forma_pago, cuenta_id,
                                   fecha, registrado_por_id, registrado_en, llave_idempotencia, anulado_en, motivo_anulacion,
                                   del_mes)
                values (gen_random_uuid(), ?, 1000, 'x', ?, ?, ?, null, current_date, gen_random_uuid(), now(),
                        gen_random_uuid(), ?, ?, false)
                """;

        assertThatThrownBy(() -> jdbc.update(insert, categoria, true, null, "EFECTIVO", null, null))
                .hasMessageContaining("ck_gasto_del_cajon");
        assertThatThrownBy(() -> jdbc.update(insert, categoria, true, turno.getId(), "TRANSFERENCIA", null, null))
                .hasMessageContaining("ck_gasto");
        assertThatThrownBy(() -> jdbc.update(insert, categoria, false, null, "TRANSFERENCIA", null, null))
                .hasMessageContaining("ck_gasto_cuenta_segun_forma_pago");
        assertThatThrownBy(() -> jdbc.update(insert, categoria, false, null, "EFECTIVO",
                java.sql.Timestamp.valueOf("2026-09-16 10:00:00"), null))
                .hasMessageContaining("ck_gasto_datos_de_anulacion");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FASE 2 — retiros, compras de caja y lo que debería haber
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("la base exige que una compra de caja sea en efectivo y de un turno, y que un retiro tenga motivo")
    void laBaseExigeCompraDeCajaYRetiro() {
        TurnoCaja turno = abrirTurno.ejecutar(Dinero.de(100_000), cajero);
        CuentaPago cuenta = registrarCuenta.ejecutar("Banco " + UUID.randomUUID().toString().substring(0, 6), personas.administrador());
        Compra compra = compraDeCaja(10_000);

        assertThat(jdbc.queryForMap("select pagada_de_caja, turno_id from compra where id = ?", compra.getId()))
                .containsEntry("pagada_de_caja", true)
                .containsEntry("turno_id", turno.getId());
        assertThatThrownBy(() -> jdbc.update("update compra set turno_id = null where id = ?", compra.getId()))
                .hasMessageContaining("ck_compra_pagada_de_caja");
        assertThatThrownBy(() -> jdbc.update(
                "update compra set forma_pago = 'TRANSFERENCIA', cuenta_id = ? where id = ?", cuenta.getId(),
                compra.getId()))
                .hasMessageContaining("ck_compra_pagada_de_caja");
        assertThatThrownBy(() -> jdbc.update("update compra set pagada_de_caja = false where id = ?", compra.getId()))
                .hasMessageContaining("ck_compra_pagada_de_caja");
        assertThatThrownBy(() -> jdbc.update("""
                insert into retiro_caja (id, turno_id, monto, motivo, registrado_por_id, registrado_en, llave_idempotencia)
                values (gen_random_uuid(), ?, 1000, '   ', gen_random_uuid(), now(), gen_random_uuid())
                """, turno.getId()))
                .hasMessageContaining("retiro_caja_motivo_check");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FASE 3 — cerrar
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("EL EJEMPLO DEL SPEC contra Postgres: $88.400 esperados, $87.000 contados, faltan $1.400, guardado columna por columna")
    void elEjemploDelSpec() {
        abrirTurno.ejecutar(Dinero.de(100_000), cajero);
        Venta deAyer = vender(70_000, 0);
        TurnoCaja ayer = turnos.abierto().orElseThrow();
        cerrarTurno.ejecutar(ayer.getId(), Dinero.de(170_000), cajero);

        TurnoCaja hoy = abrirTurno.ejecutar(Dinero.de(100_000), cajero);
        vender(223_400, 0);
        vender(0, 111_000);
        anularVenta.ejecutar(deAyer.getId(), "Cliente devolvió el repuesto", cajero);
        registrarGasto.ejecutar(ComandoRegistrarGasto.delCajon(UUID.randomUUID(), categoria("Transporte y fletes"),
                Dinero.de(15_000), "Flete Jotapartes FV-9912", cajero));
        registrarRetiro.ejecutar(UUID.randomUUID(), Dinero.de(100_000), "Se lo llevó don Rubén", false, cajero);
        compraDeCaja(50_000);

        cerrarTurno.ejecutar(hoy.getId(), Dinero.de(87_000), cajero);

        assertThat(columna("ventas_efectivo", hoy.getId())).isEqualByComparingTo("223400");
        assertThat(columna("ventas_transferencia", hoy.getId())).isEqualByComparingTo("111000");
        assertThat(columna("devoluciones_efectivo", hoy.getId())).isEqualByComparingTo("70000");
        assertThat(columna("gastos_cajon", hoy.getId())).isEqualByComparingTo("15000");
        assertThat(columna("retiros", hoy.getId())).isEqualByComparingTo("100000");
        assertThat(columna("compras_cajon", hoy.getId())).isEqualByComparingTo("50000");
        assertThat(columna("esperado", hoy.getId())).isEqualByComparingTo("88400");
        assertThat(columna("contado", hoy.getId())).isEqualByComparingTo("87000");
        assertThat(columna("diferencia", hoy.getId())).isEqualByComparingTo("-1400");
        // El de ayer no se movió con la anulación de hoy.
        assertThat(columna("ventas_efectivo", ayer.getId())).isEqualByComparingTo("70000");
        assertThat(columna("esperado", ayer.getId())).isEqualByComparingTo("170000");

        DetalleTurno detalle = consultarTurnos.detalle(hoy.getId(), personas.administrador()).orElseThrow();
        assertThat(detalle.cierre().diferencia()).isEqualTo(Dinero.de(-1_400));
        assertThat(detalle.gastos()).hasSize(1);
        assertThat(detalle.retiros()).hasSize(1);
        assertThat(detalle.compras()).singleElement().satisfies(c -> assertThat(c.total()).isEqualTo(Dinero.de(50_000)));
        assertThat(detalle.anuladasDeOtrosTurnos()).singleElement()
                .satisfies(v -> assertThat(v.id()).isEqualTo(deAyer.getId()));
        assertThat(auditoria.historialDe("TURNO_CAJA", hoy.getId())).singleElement()
                .satisfies(e -> assertThat(e.accion()).isEqualTo(AccionAuditada.CERRAR_CAJA_CON_DIFERENCIA));

        escribirObservaciones.ejecutar(hoy.getId(), "Se dio mal un cambio", personas.administrador());
        assertThat(jdbc.queryForObject("select observaciones from turno_caja where id = ?", String.class, hoy.getId()))
                .isEqualTo("Se dio mal un cambio");
        assertThat(consultarTurnos.cerrados(0, 1, personas.administrador()).elementos()).extracting(TurnoCaja::getId).containsExactly(hoy.getId());
    }

    @Test
    @DisplayName("COBROS Y CIERRE A LA VEZ, 20 rondas: cada cobro o cuenta en el arqueo del turno, o se rechaza por turno cerrado")
    void cobrosYCierreALaVez() throws Exception {
        Variante repuesto = repuestoConStock(1_000, 1_000);
        int cobrosPorRonda = 4;
        int contados = 0;
        int rechazados = 0;

        for (int ronda = 0; ronda < 20; ronda++) {
            TurnoCaja turno = abrirTurno.ejecutar(Dinero.CERO, cajero);
            List<Callable<Object>> tareas = new ArrayList<>();
            for (int i = 0; i < cobrosPorRonda; i++) {
                tareas.add(() -> cobrarVenta.ejecutar(cobro(repuesto, 1_000, 0)));
            }
            // El cierre sale en distintos momentos de los cobros (0 a 20 ms después): si siempre llegara
            // primero, la prueba no vería nunca un cobro a medias.
            long espera = ronda % 5 * 5L;
            tareas.add(() -> {
                Thread.sleep(espera);
                return cerrarTurno.ejecutar(turno.getId(), Dinero.CERO, cajero);
            });

            List<Object> resultados = aLaVez(tareas);

            assertThat(resultados.getLast()).as("ronda %d: el cierre", ronda).isInstanceOf(TurnoCaja.class);
            for (Object cobrado : resultados.subList(0, cobrosPorRonda)) {
                if (cobrado instanceof ResultadoCobro r) {
                    assertThat(r.venta().getTurnoId()).isEqualTo(turno.getId());
                    contados++;
                } else {
                    assertThat(cobrado).as("ronda %d: un cobro", ronda).isInstanceOf(SinTurnoAbiertoException.class);
                    rechazados++;
                }
            }
            BigDecimal enLaBase = jdbc.queryForObject("""
                    select coalesce(sum(p.monto), 0) from pago_venta p join venta v on v.id = p.venta_id
                    where v.turno_id = ? and p.forma = 'EFECTIVO'
                    """, BigDecimal.class, turno.getId());
            assertThat(columna("ventas_efectivo", turno.getId()))
                    .as("ronda %d: lo que el cierre contó contra lo que quedó en el turno", ronda)
                    .isEqualByComparingTo(enLaBase);
        }

        assertThat(contados + rechazados).isEqualTo(20 * cobrosPorRonda);
        System.out.printf("cobros y cierre a la vez: %d contados, %d rechazados%n", contados, rechazados);
    }

    @Test
    @DisplayName("DOS CIERRES A LA VEZ del mismo turno dejan uno; el otro dice que ya se cerró")
    void dosCierresALaVez() throws Exception {
        TurnoCaja turno = abrirTurno.ejecutar(Dinero.de(50_000), cajero);

        List<Object> resultados = aLaVez(List.of(
                () -> cerrarTurno.ejecutar(turno.getId(), Dinero.de(50_000), cajero),
                () -> cerrarTurno.ejecutar(turno.getId(), Dinero.de(40_000), cajero)));

        assertThat(resultados).filteredOn(TurnoCaja.class::isInstance).hasSize(1);
        assertThat(resultados).filteredOn(TurnoYaCerradoException.class::isInstance).hasSize(1);
        TurnoCaja ganador = (TurnoCaja) resultados.stream().filter(TurnoCaja.class::isInstance).findFirst().orElseThrow();
        assertThat(columna("contado", turno.getId())).isEqualByComparingTo(ganador.getContado().valor());
    }

    @Test
    @DisplayName("la base exige las cifras de un cierre, que sumen, y que la diferencia sea contado menos esperado")
    void laBaseExigeLasCifrasDelCierre() {
        // Con todas las partes del cierre, también las del fiado y los abonos (V21).
        String cerrado = """
                insert into turno_caja (id, abierto_por_id, abierto_en, fondo, estado, cerrado_por_id, cerrado_en,
                    ventas_efectivo, ventas_transferencia, ventas_fiado, descuentos, devoluciones_efectivo,
                    abonos_efectivo, abonos_transferencia, gastos_cajon, retiros,
                    compras_cajon, esperado, contado, diferencia)
                values (gen_random_uuid(), gen_random_uuid(), now(), 100000, 'CERRADO', gen_random_uuid(), now(),
                    ?, 0, 0, 0, 0, 0, 0, 0, 0, 0, ?, ?, ?)
                """;

        assertThatThrownBy(() -> jdbc.update("""
                insert into turno_caja (id, abierto_por_id, abierto_en, fondo, estado, cerrado_por_id, cerrado_en)
                values (gen_random_uuid(), gen_random_uuid(), now(), 0, 'CERRADO', gen_random_uuid(), now())
                """)).hasMessageContaining("ck_turno_montos_de_cierre");
        assertThatThrownBy(() -> jdbc.update(cerrado, 20_000, 110_000, 110_000, 0))
                .hasMessageContaining("ck_turno_esperado_suma_sus_partes");
        assertThatThrownBy(() -> jdbc.update(cerrado, 20_000, 120_000, 110_000, 0))
                .hasMessageContaining("ck_turno_diferencia");
        assertThatThrownBy(() -> jdbc.update(cerrado, 20_000, 120_000, -1, -120_001))
                .hasMessageContaining("contado_check");
    }
}
