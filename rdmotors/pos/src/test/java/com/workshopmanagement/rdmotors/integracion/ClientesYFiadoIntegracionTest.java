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
import com.workshopmanagement.rdmotors.caja.aplicacion.ConsultarTurnos;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.clientes.aplicacion.BuscarClientes;
import com.workshopmanagement.rdmotors.clientes.aplicacion.ClienteEncontrado;
import com.workshopmanagement.rdmotors.clientes.aplicacion.ConsultarCartera;
import com.workshopmanagement.rdmotors.clientes.aplicacion.AnularAbono;
import com.workshopmanagement.rdmotors.clientes.aplicacion.CrearCliente;
import com.workshopmanagement.rdmotors.clientes.aplicacion.RegistrarAbono;
import com.workshopmanagement.rdmotors.clientes.aplicacion.FichaCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.Abono;
import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.ClienteRepetidoException;
import com.workshopmanagement.rdmotors.clientes.dominio.DatosCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.Deuda;
import com.workshopmanagement.rdmotors.clientes.dominio.EstadoDeuda;
import com.workshopmanagement.rdmotors.clientes.dominio.FiltroCartera;
import com.workshopmanagement.rdmotors.clientes.dominio.ResumenDeCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioClientes;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioDeudas;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compras.aplicacion.ComandoRegistrarCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarProveedor;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ComandoCrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.aplicacion.CrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioCategorias;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;
import com.workshopmanagement.rdmotors.reportes.dominio.CarteraDelPeriodo;
import com.workshopmanagement.rdmotors.reportes.dominio.Periodo;
import com.workshopmanagement.rdmotors.reportes.dominio.VentaCobrada;
import com.workshopmanagement.rdmotors.reportes.dominio.puerto.RepositorioReportes;
import com.workshopmanagement.rdmotors.ventas.aplicacion.AnularVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.CobrarVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ComandoCobrarVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ConsultarVentas;
import com.workshopmanagement.rdmotors.ventas.aplicacion.DetalleVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;

/**
 * Clientes y fiado contra un Postgres real (spec 0008, fase 1): la cédula normalizada y única, lo fiado que se guarda
 * y se lee de vuelta, y el candado del cliente con dos fiados a la vez.
 *
 * <p>Contenedor propio, como las demás clases con turno: un solo turno puede estar abierto en toda la base.
 */
@SpringBootTest
@Import(UsuariosDePrueba.class)
@Testcontainers
class ClientesYFiadoIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired UsuariosDePrueba personas;
    @Autowired JdbcTemplate jdbc;
    @Autowired AbrirTurno abrirTurno;
    @Autowired RepositorioTurnos turnos;
    @Autowired ConsultarTurnos consultarTurnos;
    @Autowired CobrarVenta cobrarVenta;
    @Autowired AnularVenta anularVenta;
    @Autowired ConsultarVentas consultarVentas;
    @Autowired CrearCliente crearCliente;
    @Autowired BuscarClientes buscarClientes;
    @Autowired ConsultarCartera consultarCartera;
    @Autowired RegistrarAbono registrarAbono;
    @Autowired AnularAbono anularAbono;
    @Autowired RepositorioClientes clientes;
    @Autowired RepositorioDeudas deudas;
    @Autowired RepositorioReportes reportes;
    @Autowired CrearRepuesto crearRepuesto;
    @Autowired RegistrarCompra registrarCompra;
    @Autowired RegistrarProveedor registrarProveedor;
    @Autowired RepositorioVariantes variantes;
    @Autowired RepositorioCategorias categorias;

    private final Actor cajero;

    ClientesYFiadoIntegracionTest(@Autowired UsuariosDePrueba personas) {
        this.cajero = personas.cajero();
    }

    @BeforeEach
    void conTurnoAbierto() {
        if (turnos.abierto().isEmpty()) {
            abrirTurno.ejecutar(Dinero.de(100_000), cajero);
        }
    }

    private Cliente cliente(String nombre, String cedula) {
        return crearCliente.ejecutar(new DatosCliente(nombre, cedula, "300" + cedula.replaceAll("\\D", ""), null, null),
                cajero);
    }

    /** Lo que debería haber ahora en el cajón, leído en su transacción como lo lee la sección Caja. */
    private Dinero esperado() {
        UUID turnoId = turnos.abierto().orElseThrow().getId();
        return consultarTurnos.detalle(turnoId, cajero).orElseThrow().arqueo().esperado();
    }

    private String cedulaNueva() {
        return String.valueOf(10_000_000 + (long) (Math.random() * 89_999_999));
    }

    private Variante repuestoConStock(int stock, long precio) {
        String codigo = "FIA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Variante nuevo = crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo("REPUESTO " + codigo,
                categorias.activas().getFirst().getId(), null, codigo, "MARCA", Dinero.de(precio), 1),
                personas.administrador());
        var proveedor = registrarProveedor.ejecutar("Proveedor de pruebas de fiado", null, null, personas.administrador());
        registrarCompra.ejecutar(new ComandoRegistrarCompra(proveedor.getId(), LocalDate.of(2026, 9, 14), null,
                FormaPago.EFECTIVO, null, personas.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porTotal(nuevo.getId(), stock, Dinero.de(precio * stock / 2), null))));
        return variantes.buscar(nuevo.getId()).orElseThrow();
    }

    private ComandoCobrarVenta fiado(Variante v, Cliente c, long pagaEfectivo) {
        long total = v.getPrecio().valor().longValueExact();
        return new ComandoCobrarVenta(UUID.randomUUID(), List.of(new ComandoCobrarVenta.Renglon(v.getId(), 1, total)),
                null, pagaEfectivo == 0 ? List.of() : List.of(new ComandoCobrarVenta.Pago(FormaPago.EFECTIVO, pagaEfectivo, null)),
                c.getId(), total - pagaEfectivo, cajero);
    }

    @Test
    @DisplayName("la cédula se guarda como se escribió y se compara sin puntos; el nombre se busca sin tildes; ida y vuelta")
    void idaYVuelta() {
        String cedula = cedulaNueva();
        String conPuntos = cedula.substring(0, 2) + "." + cedula.substring(2, 5) + "." + cedula.substring(5);
        Cliente juan = crearCliente.ejecutar(new DatosCliente("José Muñoz", conPuntos, "300 555 1234", "Calle 5", null),
                cajero);

        Cliente leido = clientes.buscarPorDocumento(cedula).orElseThrow();
        assertThat(leido.getId()).isEqualTo(juan.getId());
        assertThat(leido.getDocumento()).isEqualTo(conPuntos);
        assertThat(leido.getCelularNormalizado()).isEqualTo("3005551234");
        assertThat(buscarClientes.porTexto("jose munoz")).extracting(ClienteEncontrado::id).contains(juan.getId());
        assertThat(buscarClientes.porTexto("5551234")).extracting(ClienteEncontrado::id).contains(juan.getId());
        // Los comodines se buscan tal cual: "jos%" no encuentra a José, aunque sin escapar lo encontraría.
        //
        // Antes esto se probaba con "100%_", y era una prueba que fallaba sola cada tanto: las cédulas de estas
        // pruebas son al azar, y el día que a alguna le tocaba empezar por 100 la búsqueda la encontraba — porque
        // "100%_" también se lee como CÉDULA, y ahí los signos se quitan y queda "100". Con letras no pasa: un
        // documento no se parece a "jos".
        assertThat(buscarClientes.porTexto("jos%")).isEmpty();
        assertThat(buscarClientes.porTexto("jos_")).isEmpty();
    }

    @Test
    @DisplayName("una cédula repetida, escrita de otra forma, no crea otro cliente; y la base tampoco lo deja")
    void cedulaRepetida() {
        String cedula = cedulaNueva();
        Cliente juan = cliente("Juan Pérez", cedula);

        assertThatThrownBy(() -> crearCliente.ejecutar(new DatosCliente("Juancho", " " + cedula + " ", null, null, null),
                cajero))
                .isInstanceOfSatisfying(ClienteRepetidoException.class,
                        e -> assertThat(e.getExistenteId()).isEqualTo(juan.getId()));
        assertThatThrownBy(() -> jdbc.update("""
                insert into cliente (id, nombre, nombre_normalizado, documento, documento_normalizado, creado_en,
                                     creado_por_id)
                values (?, 'Otro', 'otro', ?, ?, now(), ?)
                """, UUID.randomUUID(), cedula, cedula, cajero.id()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_cliente_documento");
    }

    @Test
    @DisplayName("cobrar fiado de punta a punta: la venta, su deuda, el comprobante, el cajón y el reporte")
    void cobrarFiado() {
        Cliente juan = cliente("Juan Pérez", cedulaNueva());
        Variante filtro = repuestoConStock(5, 80_000);
        Dinero esperadoAntes = esperado();

        Venta venta = cobrarVenta.ejecutar(fiado(filtro, juan, 30_000)).venta();

        Deuda deuda = deudas.deLaVenta(venta.getId()).orElseThrow();
        assertThat(deuda.getMonto()).isEqualTo(Dinero.de(50_000));
        assertThat(deuda.getDebeDespues()).isEqualTo(Dinero.de(50_000));
        assertThat(deuda.estado()).isEqualTo(EstadoDeuda.PENDIENTE);
        assertThat(jdbc.queryForObject("select fiado from venta where id = ?", BigDecimal.class, venta.getId()))
                .isEqualByComparingTo("50000");

        DetalleVenta detalle = consultarVentas.detalle(venta.getId(), cajero).orElseThrow();
        assertThat(detalle.cliente().nombre()).isEqualTo("Juan Pérez");
        assertThat(detalle.fiado()).isEqualTo(Dinero.de(50_000));
        assertThat(detalle.debeDespues()).isEqualTo(Dinero.de(50_000));

        assertThat(esperado())
                .as("solo entran los $30.000 en efectivo").isEqualTo(esperadoAntes.mas(Dinero.de(30_000)));

        LocalDate hoy = LocalDate.now(Periodo.ZONA);
        List<VentaCobrada> delDia = reportes.ventasCobradas(hoy.atStartOfDay(Periodo.ZONA).toInstant(),
                hoy.plusDays(1).atStartOfDay(Periodo.ZONA).toInstant());
        assertThat(delDia).filteredOn(v -> v.id().equals(venta.getId())).singleElement().satisfies(v -> {
            assertThat(v.fiado()).isEqualTo(Dinero.de(50_000));
            assertThat(v.efectivo().mas(v.transferencia()).mas(v.fiado())).isEqualTo(v.total());
        });
        assertThat(buscarClientes.porId(juan.getId()).orElseThrow().debe()).isEqualTo(Dinero.de(50_000));
    }

    @Test
    @DisplayName("SE LE FÍA CON SOLO EL NOMBRE contra Postgres: la venta, la deuda y el stock quedan igual de bien")
    void seLeFiaConSoloElNombre() {
        Cliente soloNombre = crearCliente.ejecutar(new DatosCliente("Pedro", null, null, null, null), cajero);
        Variante filtro = repuestoConStock(5, 20_000);

        Venta venta = cobrarVenta.ejecutar(fiado(filtro, soloNombre, 0)).venta();

        // La base no pide cédula ni celular para guardar la deuda: lo único obligatorio es el nombre.
        assertThat(variantes.buscar(filtro.getId()).orElseThrow().getStock()).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from venta where cliente_id = ?", Integer.class,
                soloNombre.getId())).isEqualTo(1);
        assertThat(deudas.deLaVenta(venta.getId()).orElseThrow().pendiente()).isEqualTo(Dinero.de(20_000));
        assertThat(jdbc.queryForObject("select documento is null and celular is null from cliente where id = ?",
                Boolean.class, soloNombre.getId())).isTrue();
    }

    @Test
    @DisplayName("DOS FIADOS A LA VEZ al mismo cliente: los dos quedan, y cada comprobante dice un saldo distinto")
    void dosFiadosALaVez() throws Exception {
        Cliente juan = cliente("Juan Pérez", cedulaNueva());
        Variante a = repuestoConStock(5, 20_000);
        Variante b = repuestoConStock(5, 30_000);

        List<Object> resultados = aLaVez(List.of(
                () -> cobrarVenta.ejecutar(fiado(a, juan, 0)).venta(),
                () -> cobrarVenta.ejecutar(fiado(b, juan, 0)).venta()));

        assertThat(resultados).allSatisfy(r -> assertThat(r).isInstanceOf(Venta.class));
        List<Deuda> suyas = deudas.delCliente(juan.getId());
        assertThat(suyas).hasSize(2);
        // El segundo esperó el candado del primero y vio su deuda: uno dice lo suyo, el otro los $50.000.
        Deuda segunda = suyas.stream().max(java.util.Comparator.comparing(Deuda::getDebeDespues)).orElseThrow();
        Deuda primera = suyas.stream().filter(d -> d != segunda).findFirst().orElseThrow();
        assertThat(segunda.getDebeDespues()).isEqualTo(Dinero.de(50_000));
        assertThat(primera.getDebeDespues()).isEqualTo(primera.getMonto());
        Map<UUID, Dinero> debe = deudas.debeDe(List.of(juan.getId()));
        assertThat(debe).containsEntry(juan.getId(), Dinero.de(50_000));
    }

    @Test
    @DisplayName("anular una venta fiada anula su deuda en la base y el cliente deja de deberla")
    void anularVentaFiada() {
        Cliente juan = cliente("Juan Pérez", cedulaNueva());
        Variante filtro = repuestoConStock(5, 40_000);
        Venta venta = cobrarVenta.ejecutar(fiado(filtro, juan, 0)).venta();

        anularVenta.ejecutar(venta.getId(), "Se equivocó de repuesto", cajero);

        Deuda deuda = deudas.deLaVenta(venta.getId()).orElseThrow();
        assertThat(deuda.estado()).isEqualTo(EstadoDeuda.ANULADA);
        assertThat(deuda.getAnuladaPorId()).isEqualTo(cajero.id());
        assertThat(deudas.debeDe(List.of(juan.getId()))).isEmpty();
        assertThat(variantes.buscar(filtro.getId()).orElseThrow().getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("LA BASE no deja fiar sin cliente ni fiar más que el total, aunque se salte el dominio")
    void laBaseCuidaLoFiado() {
        Variante filtro = repuestoConStock(5, 10_000);
        Venta venta = cobrarVenta.ejecutar(new ComandoCobrarVenta(UUID.randomUUID(),
                List.of(new ComandoCobrarVenta.Renglon(filtro.getId(), 1, 10_000)), null,
                List.of(new ComandoCobrarVenta.Pago(FormaPago.EFECTIVO, 10_000, null)), cajero)).venta();

        assertThatThrownBy(() -> jdbc.update("update venta set fiado = 5000 where id = ?", venta.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_venta_fiado");
        Cliente juan = cliente("Juan Pérez", cedulaNueva());
        assertThatThrownBy(() -> jdbc.update("update venta set fiado = 20000, cliente_id = ? where id = ?",
                juan.getId(), venta.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_venta_fiado");
    }

    @Test
    @DisplayName("la lista de la Cartera, hecha en SQL, dice lo mismo que la ficha, armada con las reglas del dominio")
    void laListaCuadraConLaFicha() {
        Cliente juan = cliente("Juan Pérez", cedulaNueva());
        Variante a = repuestoConStock(5, 20_000);
        Variante b = repuestoConStock(5, 30_000);
        Venta primera = cobrarVenta.ejecutar(fiado(a, juan, 0)).venta();
        cobrarVenta.ejecutar(fiado(b, juan, 10_000));
        anularVenta.ejecutar(primera.getId(), "Se equivocó de repuesto", cajero);

        ResumenDeCliente fila = consultarCartera.lista(new FiltroCartera(FiltroCartera.Vista.HISTORIAL,
                juan.getDocumento())).clientes().stream().filter(r -> r.clienteId().equals(juan.getId())).findFirst()
                .orElseThrow();
        FichaCliente ficha = consultarCartera.ficha(juan.getId()).orElseThrow();

        assertThat(fila.debe()).isEqualTo(ficha.debe()).isEqualTo(Dinero.de(20_000));
        assertThat(fila.fiadoTotal()).isEqualTo(ficha.fiadoTotal()).isEqualTo(Dinero.de(20_000));
        assertThat(fila.pagadoTotal()).isEqualTo(ficha.pagadoTotal());
        assertThat(fila.aFavor()).isEqualTo(ficha.aFavor());
        assertThat(fila.desde()).isEqualTo(ficha.desde());
        assertThat(fila.pendientes()).isEqualTo(1);
        assertThat(ficha.deudas()).extracting(FichaCliente.DeudaDeLaFicha::estado)
                .containsExactly(EstadoDeuda.PENDIENTE, EstadoDeuda.ANULADA);
        assertThat(consultarCartera.lista(FiltroCartera.losQueDeben()).clientes())
                .anySatisfy(r -> assertThat(r.clienteId()).isEqualTo(juan.getId()));
    }

    @Test
    @DisplayName("el filtro por fechas separa a quién se le fió de quién abonó (spec 0008, RF-022)")
    void filtrarPorFechas() {
        Cliente juan = cliente("Juan Pérez", cedulaNueva());
        Cliente ana = cliente("Ana Ruiz", cedulaNueva());
        cobrarVenta.ejecutar(fiado(repuestoConStock(5, 50_000), juan, 0));
        cobrarVenta.ejecutar(fiado(repuestoConStock(5, 40_000), ana, 0));
        registrarAbono.ejecutar(UUID.randomUUID(), juan.getId(), Dinero.de(10_000), FormaPago.EFECTIVO,
                null, null, null, cajero);
        LocalDate hoy = LocalDate.now(Periodo.ZONA);

        // Hoy se les fió a los dos; solo Juan abonó.
        assertThat(idsDeLaCartera(FiltroCartera.ModoFecha.VENTA, hoy, hoy)).contains(juan.getId(), ana.getId());
        assertThat(idsDeLaCartera(FiltroCartera.ModoFecha.ABONO, hoy, hoy)).contains(juan.getId())
                .doesNotContain(ana.getId());

        // La semana pasada no pasó nada: la lista sale vacía, no sale todo.
        assertThat(idsDeLaCartera(FiltroCartera.ModoFecha.VENTA, hoy.minusDays(7), hoy.minusDays(1)))
                .doesNotContain(juan.getId(), ana.getId());
        assertThat(idsDeLaCartera(FiltroCartera.ModoFecha.ABONO, hoy.minusDays(7), hoy.minusDays(1)))
                .doesNotContain(juan.getId(), ana.getId());

        // Sin período, la lista no filtra por fecha.
        assertThat(idsDeLaCartera(FiltroCartera.ModoFecha.ABONO, null, null)).contains(juan.getId(), ana.getId());
    }

    @Test
    @DisplayName("el período al revés no se consulta: dice por qué antes de tocar la base")
    void periodoAlReves() {
        LocalDate hoy = LocalDate.now(Periodo.ZONA);

        assertThatThrownBy(() -> new FiltroCartera(FiltroCartera.Vista.DEBEN, null, FiltroCartera.ModoFecha.VENTA,
                hoy, hoy.minusDays(1)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("al revés");
    }

    @Test
    @DisplayName("la cartera del reporte: lo cobrado es del período, lo por cobrar es de hoy (spec 0008, RF-024)")
    void laCarteraDelReporte() {
        var antes = carteraDeHoy();
        Cliente juan = cliente("Juan Pérez", cedulaNueva());
        cobrarVenta.ejecutar(fiado(repuestoConStock(5, 50_000), juan, 0));

        // Fiar sube lo por cobrar el mismo día, aunque no haya entrado un peso.
        assertThat(carteraDeHoy().porCobrar()).isEqualTo(antes.porCobrar().mas(Dinero.de(50_000)));

        Abono enEfectivo = registrarAbono.ejecutar(UUID.randomUUID(), juan.getId(), Dinero.de(10_000),
                FormaPago.EFECTIVO, null, null, null, cajero);
        registrarAbono.ejecutar(UUID.randomUUID(), juan.getId(), Dinero.de(15_000), FormaPago.TRANSFERENCIA,
                "Nequi", null, null, cajero);

        var conAbonos = carteraDeHoy();
        assertThat(conAbonos.abonosEfectivo()).isEqualTo(antes.abonosEfectivo().mas(Dinero.de(10_000)));
        assertThat(conAbonos.abonosTransferencia()).isEqualTo(antes.abonosTransferencia().mas(Dinero.de(15_000)));
        assertThat(conAbonos.cobrado()).isEqualTo(conAbonos.abonosEfectivo().mas(conAbonos.abonosTransferencia()));
        assertThat(conAbonos.porCobrar()).isEqualTo(antes.porCobrar().mas(Dinero.de(25_000)));
        assertThat(conAbonos.clientesQueDeben()).isEqualTo(antes.clientesQueDeben() + 1);

        // Un abono anulado no se cobró: sale de lo cobrado y la deuda vuelve a lo por cobrar.
        anularAbono.ejecutar(enEfectivo.getId(), "Se registró dos veces", personas.administrador());

        var sinElAnulado = carteraDeHoy();
        assertThat(sinElAnulado.abonosEfectivo()).isEqualTo(antes.abonosEfectivo());
        assertThat(sinElAnulado.porCobrar()).isEqualTo(antes.porCobrar().mas(Dinero.de(35_000)));

        // La semana pasada no se cobró nada, pero lo por cobrar es el mismo: no es de un período.
        LocalDate hoy = LocalDate.now(Periodo.ZONA);
        var laSemanaPasada = reportes.cartera(hoy.minusDays(7).atStartOfDay(Periodo.ZONA).toInstant(),
                hoy.minusDays(1).atStartOfDay(Periodo.ZONA).toInstant());
        assertThat(laSemanaPasada.cobrado()).isEqualTo(Dinero.CERO);
        assertThat(laSemanaPasada.porCobrar()).isEqualTo(sinElAnulado.porCobrar());
    }

    private CarteraDelPeriodo carteraDeHoy() {
        LocalDate hoy = LocalDate.now(Periodo.ZONA);
        return reportes.cartera(hoy.atStartOfDay(Periodo.ZONA).toInstant(),
                hoy.plusDays(1).atStartOfDay(Periodo.ZONA).toInstant());
    }

    private List<UUID> idsDeLaCartera(FiltroCartera.ModoFecha modo, LocalDate desde, LocalDate hasta) {
        return consultarCartera.lista(new FiltroCartera(FiltroCartera.Vista.HISTORIAL, null, modo, desde, hasta))
                .clientes().stream().map(ResumenDeCliente::clienteId).toList();
    }

    @Test
    @DisplayName("abonar de punta a punta: baja la deuda de la más vieja, entra al cajón y el recibo dice cuánto debe")
    void abonarDePuntaAPunta() {
        Cliente juan = cliente("Juan Pérez", cedulaNueva());
        Venta la41 = cobrarVenta.ejecutar(fiado(repuestoConStock(5, 50_000), juan, 0)).venta();
        Venta la57 = cobrarVenta.ejecutar(fiado(repuestoConStock(5, 30_000), juan, 0)).venta();
        Dinero esperadoAntes = esperado();

        Abono abono = registrarAbono.ejecutar(UUID.randomUUID(), juan.getId(), Dinero.de(60_000), FormaPago.EFECTIVO,
                null, "Abona el viernes", null, cajero);

        assertThat(deudas.deLaVenta(la41.getId()).orElseThrow().estado()).isEqualTo(EstadoDeuda.PAGADA);
        assertThat(deudas.deLaVenta(la57.getId()).orElseThrow().pendiente()).isEqualTo(Dinero.de(20_000));
        assertThat(abono.getDebeDespues()).isEqualTo(Dinero.de(20_000));
        assertThat(esperado()).isEqualTo(esperadoAntes.mas(Dinero.de(60_000)));

        var recibo = consultarCartera.recibo(abono.getId()).orElseThrow();
        assertThat(recibo.debeAhora()).isEqualTo(Dinero.de(20_000));
        assertThat(recibo.abono().aplicaciones()).extracting(FichaCliente.ParteAplicada::deuda)
                .containsExactly("la venta N.º " + la41.getNumero(), "la venta N.º " + la57.getNumero());
        // Lo abonado de cada deuda es la suma de las aplicaciones vigentes: la base y el dominio dicen lo mismo.
        assertThat(jdbc.queryForObject("""
                select sum(ap.monto) from aplicacion_abono ap join deuda d on d.id = ap.deuda_id
                where ap.anulada_en is null and d.cliente_id = ?
                """, BigDecimal.class, juan.getId())).isEqualByComparingTo("60000");
    }

    @Test
    @DisplayName("anular un abono lo saca del cajón y la deuda vuelve; es del administrador")
    void anularUnAbono() {
        Cliente juan = cliente("Juan Pérez", cedulaNueva());
        Venta venta = cobrarVenta.ejecutar(fiado(repuestoConStock(5, 40_000), juan, 0)).venta();
        Abono abono = registrarAbono.ejecutar(UUID.randomUUID(), juan.getId(), Dinero.de(40_000), FormaPago.EFECTIVO,
                null, null, null, cajero);
        Dinero conElAbono = esperado();

        anularAbono.ejecutar(abono.getId(), "Se registró dos veces", personas.administrador());

        assertThat(esperado()).isEqualTo(conElAbono.menos(Dinero.de(40_000)));
        assertThat(deudas.deLaVenta(venta.getId()).orElseThrow().estado()).isEqualTo(EstadoDeuda.PENDIENTE);
        assertThat(deudas.debeDe(List.of(juan.getId()))).containsEntry(juan.getId(), Dinero.de(40_000));
    }

    @Test
    @DisplayName("DOS ABONOS A LA VEZ al mismo cliente: el segundo ve la deuda ya rebajada y nunca queda debiendo menos de cero")
    void dosAbonosALaVez() throws Exception {
        Cliente juan = cliente("Juan Pérez", cedulaNueva());
        cobrarVenta.ejecutar(fiado(repuestoConStock(5, 50_000), juan, 0));

        List<Object> resultados = aLaVez(List.of(
                () -> registrarAbono.ejecutar(UUID.randomUUID(), juan.getId(), Dinero.de(30_000), FormaPago.EFECTIVO,
                        null, null, null, cajero),
                () -> registrarAbono.ejecutar(UUID.randomUUID(), juan.getId(), Dinero.de(30_000), FormaPago.EFECTIVO,
                        null, null, null, cajero)));

        long recibidos = resultados.stream().filter(r -> r instanceof Abono).count();
        assertThat(recibidos).as("uno entra; el otro ve que ya no debe tanto").isEqualTo(1);
        assertThat(resultados).anySatisfy(r -> assertThat(r).isInstanceOf(ReglaDeNegocioException.class));
        assertThat(deudas.debeDe(List.of(juan.getId()))).containsEntry(juan.getId(), Dinero.de(20_000));
    }

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
                    resultados.add(futuro.get(30, TimeUnit.SECONDS));
                } catch (java.util.concurrent.ExecutionException e) {
                    resultados.add(e.getCause());
                }
            }
            return resultados;
        } finally {
            hilos.shutdownNow();
        }
    }
}
