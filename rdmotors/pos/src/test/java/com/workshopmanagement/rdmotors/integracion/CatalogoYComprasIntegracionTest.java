package com.workshopmanagement.rdmotors.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compras.aplicacion.ComandoRegistrarCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.AnularCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.ComandoCorregirCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.ConsultarCompras;
import com.workshopmanagement.rdmotors.compras.aplicacion.CorregirCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.DesactivarCuenta;
import com.workshopmanagement.rdmotors.compras.aplicacion.DetalleCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.FilaHistorial;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarCuenta;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarProveedor;
import com.workshopmanagement.rdmotors.compras.dominio.BusquedaDeRepuesto;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.CompraModificadaException;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;
import com.workshopmanagement.rdmotors.compras.dominio.EstadoCompra;
import com.workshopmanagement.rdmotors.compras.dominio.FiltroCompras;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCuentas;
import com.workshopmanagement.rdmotors.compras.dominio.Proveedor;
import com.workshopmanagement.rdmotors.compras.dominio.RenglonesBloqueadosException;
import com.workshopmanagement.rdmotors.compras.infraestructura.DocumentosDeCompra;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ActualizarRepuesto;
import com.workshopmanagement.rdmotors.inventario.aplicacion.RepuestoEncontrado;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.inventario.aplicacion.BuscarRepuestos;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ComandoCrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.aplicacion.CrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.ConsultaInventario;
import com.workshopmanagement.rdmotors.inventario.dominio.ConteoCategoria;
import com.workshopmanagement.rdmotors.inventario.dominio.FiltroCategoria;
import com.workshopmanagement.rdmotors.inventario.dominio.MovimientoKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.ResumenInventario;
import com.workshopmanagement.rdmotors.inventario.dominio.TipoMovimiento;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioCategorias;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioProductos;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;

/**
 * Las pruebas que un doble en memoria <b>no puede</b> hacer.
 *
 * <p>Las 56 del modulo {@code domain} demuestran que las reglas son correctas. Ninguna demuestra
 * que el esquema cuadre con las entidades, que {@code Dinero} mapee a la columna correcta, que
 * {@code numeric(14,4)} conserve los decimales del costo, ni que la restriccion de codigo unico
 * exista de verdad en la base. Eso solo lo dice un Postgres real.
 *
 * <p>Levanta uno en Docker, corre las migraciones de Flyway, y deja que
 * {@code ddl-auto=validate} compare entidades contra tablas antes de que arranque el contexto.
 *
 * <p><b>Sin Docker estas pruebas no corren.</b> Es a proposito que fallen ruidosamente en vez de
 * saltarse en silencio: una prueba que se salta sola deja de proteger sin que nadie se entere.
 */
@SpringBootTest
@Import(UsuariosDePrueba.class)
@Testcontainers
class CatalogoYComprasIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired UsuariosDePrueba personas;
    @Autowired CrearRepuesto crearRepuesto;
    @Autowired RegistrarCompra registrarCompra;
    @Autowired RegistrarProveedor registrarProveedor;
    @Autowired BuscarRepuestos buscarRepuestos;
    @Autowired RepositorioCategorias categorias;
    @Autowired RepositorioVariantes variantes;
    @Autowired RepositorioKardex kardex;
    @Autowired DocumentosDeCompra documentosDeCompra;
    @Autowired RegistrarCuenta registrarCuenta;
    @Autowired RepositorioCuentas cuentas;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConsultarCompras consultarCompras;
    @Autowired CorregirCompra corregirCompra;
    @Autowired AnularCompra anularCompra;
    @Autowired TransactionTemplate transaccion;
    @Autowired DesactivarCuenta desactivarCuenta;
    @Autowired ActualizarRepuesto actualizarRepuesto;
    @Autowired RepositorioAuditoria auditoria;
    @Autowired RepositorioProductos productos;

    /** Codigo distinto por prueba: el contenedor se comparte y los datos se acumulan. */
    private String codigoUnico(String prefijo) {
        return prefijo + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private Variante repuestoNuevo(String codigo, Dinero precio) {
        Categoria filtros = categorias.activas().stream()
                .filter(c -> c.getNombre().equals("FILTROS"))
                .findFirst()
                .orElseThrow();
        return crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                "FILTRO ACEITE " + codigo, filtros.getId(), "PULSAR NS 200/FI/AS 200-DUKE 200",
                codigo, "INOKI", precio, 5), personas.administrador());
    }

    private Proveedor jotapartes() {
        return registrarProveedor.ejecutar("Importadora Jotapartes", "900123456-7", "3001234567", personas.administrador());
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("el esquema de Flyway cuadra con las entidades")
    void elEsquemaCuadra() {
        // Si ddl-auto=validate encontrara una discrepancia —una columna que falta, un tipo que no
        // coincide, Dinero mapeado donde no va— el contexto NO arrancaria y esta prueba fallaria
        // antes de llegar a esta linea. Que el contexto exista ya es media afirmacion.
        assertThat(categorias.activas())
                .as("las 16 categorias sembradas por la migracion V2")
                .hasSize(16)
                .extracting(Categoria::getNombre)
                .contains("MOTOR", "FILTROS", "FRENOS", "LUBRICANTES Y QUIMICOS");
    }

    @Test
    @DisplayName("LA PRUEBA QUE EL FALSO NO PUEDE HACER: los decimales del costo sobreviven a Postgres")
    void losDecimalesSobrevivenElViaje() {
        String codigo = codigoUnico("DEC");
        Variante repuesto = repuestoNuevo(codigo, Dinero.de(19_000));

        // 15 unidades por $200.000 → $13.333,3333 c/u
        registrarCompra.ejecutar(new ComandoRegistrarCompra(
                jotapartes().getId(), LocalDate.of(2026, 8, 13), "FV-DEC", FormaPago.EFECTIVO, null,
                personas.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porTotal(
                        repuesto.getId(), 15, Dinero.de(200_000), null))));

        // Se relee DESDE LA BASE, no del objeto que quedo en memoria
        Variante desdeLaBase = variantes.buscarPorCodigo(codigo).orElseThrow();

        assertThat(desdeLaBase.getCostoPromedio())
                .as("numeric(14,4) tiene que conservar los cuatro decimales")
                .isEqualByComparingTo("13333.3333");

        // Y lo que importa de verdad: reconstruir el total cuadra con la factura
        assertThat(Dinero.desdeUnitario(desdeLaBase.getCostoPromedio(), 15))
                .isEqualTo(Dinero.de(200_000));
    }

    @Test
    @DisplayName("una compra completa contra base real: stock, kardex y promedio")
    void compraCompleta() {
        String codigo = codigoUnico("CMP");
        Variante repuesto = repuestoNuevo(codigo, Dinero.de(3_500));
        Proveedor proveedor = jotapartes();

        registrarCompra.ejecutar(new ComandoRegistrarCompra(
                proveedor.getId(), LocalDate.of(2026, 8, 13), "FV-1", FormaPago.EFECTIVO, null,
                personas.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porUnitario(
                        repuesto.getId(), 10, new java.math.BigDecimal("1000"), null))));

        Compra segunda = registrarCompra.ejecutar(new ComandoRegistrarCompra(
                proveedor.getId(), LocalDate.of(2026, 9, 1), "FV-2", FormaPago.EFECTIVO, null,
                personas.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porUnitario(
                        repuesto.getId(), 10, new java.math.BigDecimal("2000"),
                        Dinero.de(4_200)))));

        Variante desdeLaBase = variantes.buscarPorCodigo(codigo).orElseThrow();

        assertThat(desdeLaBase.getStock()).isEqualTo(20);
        assertThat(desdeLaBase.getCostoPromedio()).isEqualByComparingTo("1500.0000");
        // El precio de venta sí venía en la segunda compra, así que se actualizó
        assertThat(desdeLaBase.getPrecio()).isEqualTo(Dinero.de(4_200));

        List<MovimientoKardex> historial = kardex.historialDe(repuesto.getId());
        assertThat(historial).hasSize(2);
        assertThat(historial.get(0).getSaldoDespues()).isEqualTo(10);
        assertThat(historial.get(0).getCostoPromedioDespues()).isEqualByComparingTo("1000.0000");
        assertThat(historial.get(1).getSaldoDespues()).isEqualTo(20);
        assertThat(historial.get(1).getCostoPromedioDespues()).isEqualByComparingTo("1500.0000");
        assertThat(historial.get(1).getTipo()).isEqualTo(TipoMovimiento.COMPRA);
        assertThat(historial.get(1).getOrigenId()).isEqualTo(segunda.getId());
    }

    @Test
    @DisplayName("el codigo unico lo hace cumplir LA BASE, no solo la aplicacion")
    void laBaseImponeElCodigoUnico() {
        String codigo = codigoUnico("UNQ");
        Variante primera = repuestoNuevo(codigo, Dinero.de(6_000));

        // Se salta el caso de uso a proposito, para probar la restriccion del esquema y no la
        // validacion de la aplicacion. Si manana alguien inserta por otro camino —un importador,
        // un script— la base tiene que seguir protegiendo.
        Variante clandestina = Variante.nueva(primera.getProducto(), codigo, "OTRA MARCA",
                Dinero.de(9_000), 5);

        assertThatThrownBy(() -> variantes.guardar(clandestina))
                .as("la restriccion UNIQUE de la migracion V1")
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("la busqueda por texto trae el concepto en la misma consulta")
    void busquedaPorTexto() {
        String codigo = codigoUnico("BUS");
        repuestoNuevo(codigo, Dinero.de(6_000));

        var resultados = buscarRepuestos.porTexto("FILTRO ACEITE " + codigo);

        assertThat(resultados).isNotEmpty();
        var encontrado = resultados.get(0);
        // Leer el nombre exige el concepto. Si el adaptador no hiciera join fetch, esto seria una
        // consulta extra por fila — o reventaria fuera de la transaccion.
        assertThat(encontrado.nombre()).isEqualTo("FILTRO ACEITE " + codigo);
        assertThat(encontrado.aplicacion()).isEqualTo("PULSAR NS 200/FI/AS 200-DUKE 200");
        assertThat(encontrado.costoDesconocido())
                .as("recien creado, nunca comprado: el costo es null, no cero")
                .isTrue();
    }

    @Test
    @DisplayName("buscar por codigo exacto encuentra lo que se acaba de crear")
    void busquedaPorCodigo() {
        String codigo = codigoUnico("COD");
        repuestoNuevo(codigo, Dinero.de(6_000));

        assertThat(buscarRepuestos.porCodigoExacto(codigo.toLowerCase()))
                .as("se copia de una factura: tolera minusculas")
                .isPresent();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FASE 4 — el repuesto nace durante la compra
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("un renglon crea el repuesto y todo queda persistido junto")
    void creaElRepuestoDuranteLaCompra() {
        String codigo = codigoUnico("NUEVO");
        Categoria frenos = categorias.activas().stream()
                .filter(c -> c.getNombre().equals("FRENOS")).findFirst().orElseThrow();

        registrarCompra.ejecutar(new ComandoRegistrarCompra(
                jotapartes().getId(), LocalDate.of(2026, 8, 13), "FV-NUEVO", FormaPago.EFECTIVO, null,
                personas.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porUnitarioCreando(
                        ComandoCrearRepuesto.conConceptoNuevo(
                                "PASTILLAS FRENO " + codigo, frenos.getId(), "AK150 RTX UNISHOCK",
                                codigo, "CBI", Dinero.de(24_000), 4),
                        6, new java.math.BigDecimal("14200")))));

        Variante desdeLaBase = variantes.buscarPorCodigo(codigo).orElseThrow();
        assertThat(desdeLaBase.getStock()).isEqualTo(6);
        assertThat(desdeLaBase.getCostoPromedio()).isEqualByComparingTo("14200.0000");
        assertThat(desdeLaBase.getPrecio()).isEqualTo(Dinero.de(24_000));
        assertThat(kardex.historialDe(desdeLaBase.getId())).hasSize(1);
    }

    @Test
    @DisplayName("LA PRUEBA QUE EL FALSO NO PUEDE HACER: si la compra falla, el repuesto tampoco queda")
    void siLaCompraFallaElRepuestoNoQueda() {
        String codigo = codigoUnico("ROLLBACK");

        // Renglon 1 crea el repuesto; renglon 2 apunta a una variante que no existe y revienta.
        // Como los dos casos de uso comparten transaccion, la creacion tiene que deshacerse.
        assertThatThrownBy(() -> registrarCompra.ejecutar(new ComandoRegistrarCompra(
                jotapartes().getId(), LocalDate.of(2026, 8, 13), "FV-FALLA", FormaPago.EFECTIVO, null,
                personas.administrador(),
                List.of(
                        ComandoRegistrarCompra.Linea.porUnitarioCreando(
                                ComandoCrearRepuesto.conConceptoNuevo(
                                        "ALGO QUE NO DEBE QUEDAR " + codigo, categorias.activas().getFirst().getId(), null,
                                        codigo, "CBI", Dinero.de(24_000), 4),
                                6, new java.math.BigDecimal("14200")),
                        ComandoRegistrarCompra.Linea.porUnitario(
                                UUID.randomUUID(), 3, new java.math.BigDecimal("1000"), null)))))
                .hasMessageContaining("no existe");

        // Un doble en memoria NO habria detectado esto: sus mapas no se deshacen.
        assertThat(variantes.buscarPorCodigo(codigo))
                .as("el repuesto creado en el renglon 1 debe haberse deshecho con la transaccion")
                .isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FASE 6 — inventario y kardex
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("el inventario pagina en la base, trae la categoria y encuentra por codigo parcial")
    void inventarioContraPostgres() {
        String codigo = codigoUnico("INV");
        Categoria filtros = categorias.activas().stream()
                .filter(c -> c.getNombre().equals("FILTROS")).findFirst().orElseThrow();
        // El nombre NO contiene el codigo, para que solo pueda encontrarse por el codigo
        crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                "REPUESTO DE PRUEBA", filtros.getId(), null, codigo, "INOKI", Dinero.de(6_000), 5), personas.administrador());

        // Codigo parcial: el buscador de la compra no lo hace, el inventario si
        var pagina = buscarRepuestos.inventario(codigo.substring(4), false, 0, 25);

        assertThat(pagina.total()).isEqualTo(1);
        RepuestoEncontrado encontrado = pagina.elementos().get(0);
        assertThat(encontrado.codigo()).isEqualTo(codigo);
        // La categoria y el minimo son lo que el formulario de corregir ficha necesita para no borrarlos
        assertThat(encontrado.categoria()).isEqualTo("FILTROS");
        assertThat(encontrado.categoriaId()).isNotNull();
        assertThat(encontrado.stockMinimo()).isEqualTo(5);
    }

    @Test
    @DisplayName("sin texto trae todo, y el conteo de la base cuadra con las paginas")
    void inventarioSinTextoPagina() {
        repuestoNuevo(codigoUnico("PAG"), Dinero.de(1_000));
        repuestoNuevo(codigoUnico("PAG"), Dinero.de(1_000));

        var primera = buscarRepuestos.inventario("", false, 0, 1);
        var todas = buscarRepuestos.inventario("", false, 0, 100);

        // La consulta de conteo esta escrita a mano: si se desalineara del listado, esto lo dice
        assertThat(primera.elementos()).hasSize(1);
        assertThat(primera.total()).isEqualTo(todas.elementos().size()).isGreaterThanOrEqualTo(2);
        assertThat(primera.totalPaginas()).isEqualTo((int) primera.total());
    }

    // ── Spec 0005, fase 2: por categoría ─────────────────────────────────────

    /** Un repuesto de antes de la regla: la categoría se le quita por SQL, como están los viejos en QA. */
    private Variante sinCategoriaEnLaBase(String nombre, String codigo) {
        Variante v = crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                nombre, categorias.activas().getFirst().getId(), null, codigo, "GENERICO", Dinero.de(3_000), 1), personas.administrador());
        jdbc.update("update producto set categoria_id = null where id = ?", v.getProducto().getId());
        return v;
    }

    private ConsultaInventario enCategoria(String texto, FiltroCategoria categoria) {
        return new ConsultaInventario(texto, false, categoria, false);
    }

    @Test
    @DisplayName("SPEC 0005 · LOS NÚMEROS DE LAS CATEGORÍAS CUADRAN CON LA LISTA: suman el total, y cada uno es lo que lista su filtro")
    void conteoPorCategoriaDeAcuerdoConLaLista() {
        String s = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        UUID filtros = categorias.activas().stream().filter(c -> c.getNombre().equals("FILTROS")).findFirst().orElseThrow().getId();
        UUID lubricantes = categorias.activas().stream().filter(c -> c.getNombre().equals("LUBRICANTES Y QUIMICOS")).findFirst().orElseThrow().getId();
        crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo("FILTRO DE ACEITE " + s, filtros, null, "FA1-" + s, "INOKI", Dinero.de(9_000), 1), personas.administrador());
        crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo("FILTRO DE ACEITE " + s, lubricantes, null, "FA2-" + s, "OTRA", Dinero.de(9_000), 1), personas.administrador());
        crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo("ACEITE 20W50 " + s, lubricantes, null, "AC1-" + s, "MOTUL", Dinero.de(32_000), 1), personas.administrador());
        sinCategoriaEnLaBase("ACEITE SUELTO " + s, "AC2-" + s);

        String m = s.toLowerCase();
        for (String texto : List.of("aceite " + m, "ACEITE 20W50 " + s, "fa1-" + m, "motul", "inoki", "", "no-existe-" + m)) {
            var conteo = buscarRepuestos.conteoPorCategoria(texto);
            long total = buscarRepuestos.inventario(texto, false, 0, 1).total();
            assertThat(conteo.stream().mapToLong(ConteoCategoria::repuestos).sum())
                    .as("«%s»: las categorías suman el total de la lista", texto).isEqualTo(total);
            assertThat(conteo).as("«%s»: sin categorías vacías", texto).allSatisfy(c -> assertThat(c.repuestos()).isPositive());
            for (ConteoCategoria c : conteo) {
                FiltroCategoria filtro = c.categoriaId() == null ? FiltroCategoria.sinCategoria() : FiltroCategoria.de(c.categoriaId());
                assertThat(buscarRepuestos.inventario(enCategoria(texto, filtro), 0, 1).total())
                        .as("«%s» en %s: el número es lo que lista su filtro", texto, c.nombre()).isEqualTo(c.repuestos());
            }
            if (!conteo.isEmpty() && conteo.stream().anyMatch(c -> c.categoriaId() == null)) {
                assertThat(conteo.getLast().categoriaId()).as("«%s»: los sin categoría al final", texto).isNull();
            }
        }

        // Con este texto, exactos: "aceite <s>" solo está en los dos FILTRO DE ACEITE, uno en cada categoría.
        assertThat(buscarRepuestos.conteoPorCategoria("aceite " + m))
                .extracting(ConteoCategoria::nombre, ConteoCategoria::repuestos)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("FILTROS", 1L),
                        org.assertj.core.groups.Tuple.tuple("LUBRICANTES Y QUIMICOS", 1L));
    }

    @Test
    @DisplayName("SPEC 0005 · sin categoría y con stock primero, en la base, por páginas y con orden estable")
    void sinCategoriaYStockPrimeroContraPostgres() {
        String s = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Variante viejo = sinCategoriaEnLaBase("TORNILLO VIEJO " + s, "TV-" + s);
        UUID filtros = categorias.activas().stream().filter(c -> c.getNombre().equals("FILTROS")).findFirst().orElseThrow().getId();
        Variante agotado = crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo("AAA AGOTADO " + s, filtros, null, "AG-" + s, "X", Dinero.de(1_000), 1), personas.administrador());
        Variante conStock = crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo("ZZZ CON STOCK " + s, filtros, null, "CS-" + s, "X", Dinero.de(1_000), 1), personas.administrador());
        registrarCompra.ejecutar(new ComandoRegistrarCompra(jotapartes().getId(), LocalDate.of(2026, 9, 15), "FV-STK-" + s,
                FormaPago.EFECTIVO, null, personas.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porUnitario(conStock.getId(), 4, new java.math.BigDecimal("500"), null))));

        assertThat(buscarRepuestos.inventario(enCategoria(s.toLowerCase(), FiltroCategoria.sinCategoria()), 0, 25).elementos())
                .extracting(RepuestoEncontrado::id).containsExactly(viejo.getId());
        assertThat(buscarRepuestos.inventario(enCategoria(s.toLowerCase(), FiltroCategoria.todas()), 0, 25).total())
                .as("el left join: el viejo sin categoría también sale en todas").isEqualTo(3);

        var consulta = new ConsultaInventario(s.toLowerCase(), false, FiltroCategoria.de(filtros), true);
        assertThat(buscarRepuestos.inventario(consulta, 0, 25).elementos())
                .as("con stock primero: ZZZ con stock antes que AAA agotado")
                .extracting(RepuestoEncontrado::id).containsExactly(conStock.getId(), agotado.getId());
        var primera = buscarRepuestos.inventario(consulta, 0, 1);
        var segunda = buscarRepuestos.inventario(consulta, 1, 1);
        assertThat(primera.total()).isEqualTo(2);
        assertThat(List.of(primera.elementos().getFirst().id(), segunda.elementos().getFirst().id()))
                .containsExactly(conStock.getId(), agotado.getId());
    }

    @Test
    @DisplayName("solo stock bajo filtra en la base comparando stock contra el minimo de cada uno")
    void inventarioSoloStockBajo() {
        String codigo = codigoUnico("BAJO");
        Variante repuesto = repuestoNuevo(codigo, Dinero.de(6_000));   // stock 0, minimo 5

        assertThat(buscarRepuestos.inventario(codigo, true, 0, 25).total()).isEqualTo(1);

        registrarCompra.ejecutar(new ComandoRegistrarCompra(
                jotapartes().getId(), LocalDate.of(2026, 8, 13), "FV-BAJO", FormaPago.EFECTIVO, null,
                personas.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porUnitario(
                        repuesto.getId(), 20, new java.math.BigDecimal("3000"), null))));

        assertThat(buscarRepuestos.inventario(codigo, true, 0, 25).total()).isZero();
        assertThat(buscarRepuestos.inventario(codigo, false, 0, 25).total()).isEqualTo(1);
    }

    @Test
    @DisplayName("el resumen suma en la base y NO cuenta como cero lo que no tiene costo")
    void resumenContraPostgres() {
        // El contenedor se comparte entre pruebas: se mide la DIFERENCIA, no el total absoluto
        ResumenInventario antes = variantes.resumen();

        Variante comprado = repuestoNuevo(codigoUnico("RES"), Dinero.de(19_000));
        repuestoNuevo(codigoUnico("RES"), Dinero.de(19_000));           // nunca comprado

        registrarCompra.ejecutar(new ComandoRegistrarCompra(
                jotapartes().getId(), LocalDate.of(2026, 8, 13), "FV-RES", FormaPago.EFECTIVO, null,
                personas.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porTotal(
                        comprado.getId(), 15, Dinero.de(200_000), null))));

        ResumenInventario despues = variantes.resumen();

        assertThat(despues.referencias() - antes.referencias()).isEqualTo(2);
        assertThat(despues.unidades() - antes.unidades()).isEqualTo(15);
        // 15 x 13.333,3333 = 199.999,9995: la base suma con los cuatro decimales
        assertThat(despues.valor().subtract(antes.valor())).isEqualByComparingTo("199999.9995");
        // El nunca comprado tiene stock 0: no deja unidades fuera del valor
        assertThat(despues.sinCosto() - antes.sinCosto()).isZero();
        assertThat(despues.conStockBajo() - antes.conStockBajo())
                .as("el nunca comprado esta en 0 con minimo 5; el comprado tiene 15")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("el kardex sabe de que factura salio cada compra, y que precio le fijo")
    void documentosDelKardex() {
        String codigo = codigoUnico("DOC");
        Variante repuesto = repuestoNuevo(codigo, Dinero.de(3_500));
        Proveedor proveedor = jotapartes();

        Compra sinCambioDePrecio = registrarCompra.ejecutar(new ComandoRegistrarCompra(
                proveedor.getId(), LocalDate.of(2026, 8, 13), "FV-DOC-1", FormaPago.EFECTIVO, null,
                personas.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porUnitario(
                        repuesto.getId(), 10, new java.math.BigDecimal("1000"), null))));
        Compra conCambioDePrecio = registrarCompra.ejecutar(new ComandoRegistrarCompra(
                proveedor.getId(), LocalDate.of(2026, 9, 1), "FV-DOC-2", FormaPago.EFECTIVO, null,
                personas.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porUnitario(
                        repuesto.getId(), 10, new java.math.BigDecimal("2000"),
                        Dinero.de(4_200)))));

        List<MovimientoKardex> entradas = kardex.historialDe(repuesto.getId());
        var descripcion = documentosDeCompra.describir(
                List.of(sinCambioDePrecio.getId(), conCambioDePrecio.getId()),
                entradas.stream().map(MovimientoKardex::getId).toList());

        assertThat(descripcion.compras()).hasSize(2);
        var primero = descripcion.compras().get(sinCambioDePrecio.getId());
        assertThat(primero.proveedor()).isEqualTo("Importadora Jotapartes");
        assertThat(primero.factura()).isEqualTo("FV-DOC-1");
        assertThat(primero.fechaDocumento()).isEqualTo(LocalDate.of(2026, 8, 13));
        assertThat(primero.anulada()).isFalse();

        // El precio es por movimiento de entrada: la primera no lo tocó, la segunda fijó $4.200.
        assertThat(descripcion.precioPorEntrada()).doesNotContainKey(entradas.get(0).getId());
        assertThat(descripcion.precioPorEntrada()).containsEntry(entradas.get(1).getId(), 4_200L);

        assertThat(documentosDeCompra.describir(List.of(), List.of(UUID.randomUUID())).precioPorEntrada())
                .isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SPEC 0002 · FASE 1 — forma de pago y cuentas
    // ─────────────────────────────────────────────────────────────────────────

    private Compra compraPagada(FormaPago forma, UUID cuentaId, String factura) {
        Variante repuesto = repuestoNuevo(codigoUnico("PAGO"), Dinero.de(6_000));
        return registrarCompra.ejecutar(new ComandoRegistrarCompra(
                jotapartes().getId(), LocalDate.of(2026, 9, 10), factura, forma, cuentaId,
                personas.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porTotal(
                        repuesto.getId(), 4, Dinero.de(20_000), null))));
    }

    @Test
    @DisplayName("una compra por transferencia queda en la base con su cuenta")
    void transferenciaIdaYVuelta() {
        CuentaPago nequi = registrarCuenta.ejecutar("Nequi del dueño " + codigoUnico("CTA"), personas.administrador());

        Compra compra = compraPagada(FormaPago.TRANSFERENCIA, nequi.getId(), "FV-TRANSF");

        var fila = jdbc.queryForMap(
                "select forma_pago, cuenta_id from compra where id = ?", compra.getId());
        assertThat(fila.get("forma_pago")).isEqualTo("TRANSFERENCIA");
        assertThat(fila.get("cuenta_id")).isEqualTo(nequi.getId());
    }

    @Test
    @DisplayName("LA BASE exige que la cuenta cuadre con la forma de pago, no solo la aplicación")
    void laBaseExigeCuentaSegunFormaDePago() {
        Compra enEfectivo = compraPagada(FormaPago.EFECTIVO, null, "FV-CHECK");
        CuentaPago cuenta = registrarCuenta.ejecutar("Bancolombia " + codigoUnico("CHK"), personas.administrador());

        // Se salta el dominio a propósito: un script o un importador que escriba directo en la base
        // tampoco puede dejar una transferencia sin cuenta, ni un efectivo con cuenta.
        assertThatThrownBy(() -> jdbc.update(
                "update compra set forma_pago = 'TRANSFERENCIA' where id = ?", enEfectivo.getId()))
                .as("transferencia sin cuenta")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "update compra set cuenta_id = ? where id = ?", cuenta.getId(), enEfectivo.getId()))
                .as("efectivo con cuenta")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("LA BASE rechaza la misma cuenta escrita con otras mayúsculas")
    void laBaseImponeElNombreUnicoDeCuenta() {
        String sufijo = codigoUnico("UNQ");
        registrarCuenta.ejecutar("Nequi del dueño " + sufijo, personas.administrador());

        // Saltándose el caso de uso: la regla también la tiene que sostener el índice único.
        assertThatThrownBy(() -> cuentas.guardar(CuentaPago.nueva("NEQUI DEL DUEÑO " + sufijo)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("una compra nueva no hereda efectivo en silencio: la columna no tiene default")
    void formaDePagoSinDefault() {
        Compra compra = compraPagada(FormaPago.EFECTIVO, null, "FV-DEFAULT");

        // Una fila insertada sin forma_pago tiene que fallar. El default de V3 solo existió para
        // rellenar las compras viejas.
        assertThatThrownBy(() -> jdbc.update("""
                insert into compra (id, proveedor_id, fecha_documento, fecha_registro, total,
                                    registrado_por_id)
                select gen_random_uuid(), proveedor_id, fecha_documento, fecha_registro, total,
                       registrado_por_id
                from compra where id = ?
                """, compra.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SPEC 0002 · FASE 2 — historial y detalle
    // ─────────────────────────────────────────────────────────────────────────

    private Compra compraDe(Proveedor proveedor, LocalDate fecha, String factura, FormaPago forma,
                            UUID cuentaId, List<ComandoRegistrarCompra.Linea> lineas) {
        return registrarCompra.ejecutar(new ComandoRegistrarCompra(
                proveedor.getId(), fecha, factura, forma, cuentaId, personas.administrador(), lineas));
    }

    private ComandoRegistrarCompra.Linea renglon(int cantidad, long total) {
        Variante repuesto = repuestoNuevo(codigoUnico("HIS"), Dinero.de(9_000));
        return ComandoRegistrarCompra.Linea.porTotal(repuesto.getId(), cantidad, Dinero.de(total), null);
    }

    private List<UUID> idsDe(FiltroCompras filtro) {
        return consultarCompras.historial(filtro, 0, 25, personas.administrador()).elementos().stream().map(r -> r.id()).toList();
    }

    @Test
    @DisplayName("el historial filtra en Postgres por cada criterio, y sin ninguno también")
    void historialFiltraContraPostgres() {
        // Un proveedor propio por prueba: el contenedor se comparte y las compras se acumulan.
        Proveedor proveedor = jotapartes();
        Proveedor otro = jotapartes();
        CuentaPago nequi = registrarCuenta.ejecutar("Nequi " + codigoUnico("HIS"), personas.administrador());
        String sufijo = codigoUnico("FV");

        Compra agosto = compraDe(proveedor, LocalDate.of(2026, 8, 10), "AGO-" + sufijo,
                FormaPago.EFECTIVO, null, List.of(renglon(2, 10_000)));
        Compra septiembre = compraDe(proveedor, LocalDate.of(2026, 9, 2), "SEP-" + sufijo,
                FormaPago.TRANSFERENCIA, nequi.getId(), List.of(renglon(3, 30_000), renglon(1, 5_000)));
        compraDe(otro, LocalDate.of(2026, 9, 3), "OTRO-" + sufijo, FormaPago.EFECTIVO, null,
                List.of(renglon(1, 1_000)));

        var delProveedor = consultarCompras.historial(
                new FiltroCompras(proveedor.getId(), null, null, null, null, null, null, null), 0, 25, personas.administrador());
        assertThat(delProveedor.total()).isEqualTo(2);
        assertThat(delProveedor.elementos()).extracting(r -> r.id())
                .as("la factura más reciente primero")
                .containsExactly(septiembre.getId(), agosto.getId());

        var fila = delProveedor.elementos().get(0);
        assertThat(fila.renglones()).isEqualTo(2);
        assertThat(fila.total()).isEqualTo(Dinero.de(35_000));
        assertThat(fila.formaPago()).isEqualTo(FormaPago.TRANSFERENCIA);
        assertThat(fila.cuenta()).isEqualTo(nequi.getNombre());

        assertThat(idsDe(new FiltroCompras(proveedor.getId(), LocalDate.of(2026, 9, 1), null, null, null, null, null, null)))
                .as("desde").containsExactly(septiembre.getId());
        assertThat(idsDe(new FiltroCompras(proveedor.getId(), null, LocalDate.of(2026, 8, 31), null, null, null, null, null)))
                .as("hasta").containsExactly(agosto.getId());
        assertThat(idsDe(new FiltroCompras(proveedor.getId(), null, null, FormaPago.EFECTIVO, null, null, null, null)))
                .as("forma de pago").containsExactly(agosto.getId());
        assertThat(idsDe(new FiltroCompras(null, null, null, null, nequi.getId(), null, null, null)))
                .as("cuenta").containsExactly(septiembre.getId());
        assertThat(idsDe(new FiltroCompras(null, null, null, null, null, "sep-" + sufijo.toLowerCase(), null, null)))
                .as("factura parcial, sin distinguir mayúsculas").containsExactly(septiembre.getId());

        // Sin filtros: la consulta sin WHERE. El conteo tiene que cuadrar con lo que se lista.
        var todas = consultarCompras.historial(FiltroCompras.sinFiltros(), 0, 100, personas.administrador());
        assertThat(todas.total()).isGreaterThanOrEqualTo(3);
        assertThat(consultarCompras.historial(FiltroCompras.sinFiltros(), 0, 1, personas.administrador()).total())
                .isEqualTo(todas.total());
    }

    @Test
    @DisplayName("el detalle trae los renglones en el orden en que se capturaron, y su total cuadra")
    void detalleContraPostgres() {
        var primero = renglon(15, 200_000);
        var segundo = renglon(6, 85_200);
        var tercero = renglon(1, 3_000);
        Compra compra = compraDe(jotapartes(), LocalDate.of(2026, 9, 5), codigoUnico("FV"),
                FormaPago.EFECTIVO, null, List.of(primero, segundo, tercero));

        // Se lee fuera de cualquier transacción: si el detalle dependiera de relaciones perezosas,
        // esto reventaría aquí y no en la pantalla.
        var detalle = consultarCompras.detalle(compra.getId(), personas.administrador()).orElseThrow();

        assertThat(detalle.renglones()).extracting(r -> r.repuesto().id())
                .as("el orden de la factura, no el que devuelva la base")
                .containsExactly(primero.varianteId(), segundo.varianteId(), tercero.varianteId());
        assertThat(detalle.renglones().get(0).costoUnitario()).isEqualByComparingTo("13333.3333");
        assertThat(detalle.total())
                .isEqualTo(detalle.renglones().stream()
                        .map(r -> r.costoTotal()).reduce(Dinero.CERO, Dinero::mas))
                .isEqualTo(Dinero.de(288_200));
        assertThat(detalle.renglones().get(0).repuesto().stock()).isEqualTo(15);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SPEC 0002 · FASES 3, 4 y 5 — auditoría, reversión, corregir y anular
    // ─────────────────────────────────────────────────────────────────────────

    private ComandoCorregirCompra.Linea renglonCorregido(UUID lineaId, UUID varianteId, int cantidad,
                                                         long costoUnitario) {
        return new ComandoCorregirCompra.Linea(lineaId, ComandoRegistrarCompra.Linea.porUnitario(
                varianteId, cantidad, java.math.BigDecimal.valueOf(costoUnitario), null));
    }

    private ComandoCorregirCompra correccion(DetalleCompra detalle,
                                             List<ComandoCorregirCompra.Linea> lineas) {
        return new ComandoCorregirCompra(detalle.id(), detalle.version(), "error al digitar",
                personas.administrador(), detalle.proveedorId(), detalle.fechaDocumento(),
                detalle.numeroFactura(), detalle.formaPago(), detalle.cuentaId(), lineas);
    }

    @Test
    @DisplayName("una corrección contra Postgres: kardex por secuencia, renglón viejo como historia y rastro en jsonb")
    void correccionContraPostgres() {
        Variante filtro = repuestoNuevo(codigoUnico("COR"), Dinero.de(6_000));
        Variante pastillas = repuestoNuevo(codigoUnico("COR"), Dinero.de(24_000));
        Compra compra = compraDe(jotapartes(), LocalDate.of(2026, 9, 5), codigoUnico("FV"),
                FormaPago.EFECTIVO, null, List.of(
                        ComandoRegistrarCompra.Linea.porUnitario(filtro.getId(), 100,
                                new java.math.BigDecimal("2000"), null),
                        ComandoRegistrarCompra.Linea.porUnitario(pastillas.getId(), 6,
                                new java.math.BigDecimal("14200"), null)));
        var antes = consultarCompras.detalle(compra.getId(), personas.administrador()).orElseThrow();

        var resultado = corregirCompra.ejecutar(correccion(antes, List.of(
                renglonCorregido(antes.renglones().get(0).lineaId(), filtro.getId(), 10, 2_000),
                renglonCorregido(antes.renglones().get(1).lineaId(), pastillas.getId(), 6, 14_200))));
        assertThat(resultado.avisos()).isEmpty();

        var despues = consultarCompras.detalle(compra.getId(), personas.administrador()).orElseThrow();
        assertThat(despues.version()).as("la corrección subió la versión").isEqualTo(antes.version() + 1);
        assertThat(despues.renglones()).hasSize(2);
        assertThat(despues.reemplazados()).hasSize(1);
        assertThat(despues.renglones().get(0).cantidad()).isEqualTo(10);
        assertThat(despues.renglones().get(0).posicion()).isEqualTo(0);
        assertThat(despues.total()).isEqualTo(Dinero.de(20_000 + 6 * 14_200));

        Variante filtroEnBase = variantes.buscarPorCodigo(filtro.getCodigo()).orElseThrow();
        assertThat(filtroEnBase.getStock()).isEqualTo(10);
        assertThat(filtroEnBase.getCostoPromedio()).isEqualByComparingTo("2000.0000");

        // La reversión y la entrada corregida comparten instante: el orden lo da la secuencia.
        List<MovimientoKardex> movimientos = kardex.historialDe(filtro.getId());
        assertThat(movimientos).extracting(MovimientoKardex::getTipo, MovimientoKardex::getCantidadDelta)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(TipoMovimiento.COMPRA, 100),
                        org.assertj.core.groups.Tuple.tuple(TipoMovimiento.CORRECCION_COMPRA, -100),
                        org.assertj.core.groups.Tuple.tuple(TipoMovimiento.COMPRA, 10));
        assertThat(movimientos.get(1).getCreadoEn()).isEqualTo(movimientos.get(2).getCreadoEn());
        assertThat(movimientos.get(1).getSecuencia()).isLessThan(movimientos.get(2).getSecuencia());
        assertThat(kardex.historialDe(pastillas.getId())).as("pastillas no cambió").hasSize(1);

        // El rastro vuelve de jsonb con sus números intactos.
        var evento = despues.correcciones().getLast();
        assertThat(evento.motivo()).isEqualTo("error al digitar");
        @SuppressWarnings("unchecked")
        var renglonesAntes = (List<java.util.Map<String, Object>>) evento.antes().get("renglones");
        assertThat(((Number) renglonesAntes.get(0).get("cantidad")).intValue()).isEqualTo(100);
        assertThat((java.math.BigDecimal) renglonesAntes.get(0).get("costoUnitario"))
                .isEqualByComparingTo("2000.0000");
    }

    @Test
    @DisplayName("CONCURRENCIA REAL: dos correcciones con la misma versión, la segunda se rechaza")
    void versionEnConflicto() {
        Variante filtro = repuestoNuevo(codigoUnico("VER"), Dinero.de(6_000));
        Compra compra = compraDe(jotapartes(), LocalDate.of(2026, 9, 5), "FV-VER",
                FormaPago.EFECTIVO, null, List.of(ComandoRegistrarCompra.Linea.porUnitario(
                        filtro.getId(), 10, new java.math.BigDecimal("2000"), null)));
        var abiertaPorLosDos = consultarCompras.detalle(compra.getId(), personas.administrador()).orElseThrow();

        corregirCompra.ejecutar(new ComandoCorregirCompra(compra.getId(), abiertaPorLosDos.version(),
                "número mal", personas.administrador(), abiertaPorLosDos.proveedorId(),
                abiertaPorLosDos.fechaDocumento(), "FV-VER-1", FormaPago.EFECTIVO, null, null));

        assertThatThrownBy(() -> corregirCompra.ejecutar(new ComandoCorregirCompra(compra.getId(),
                abiertaPorLosDos.version(), "otro número", personas.administrador(),
                abiertaPorLosDos.proveedorId(), abiertaPorLosDos.fechaDocumento(), "FV-VER-2",
                FormaPago.EFECTIVO, null, null)))
                .isInstanceOf(CompraModificadaException.class);
        assertThat(consultarCompras.detalle(compra.getId(), personas.administrador()).orElseThrow().numeroFactura())
                .as("la primera no se pisó").isEqualTo("FV-VER-1");
    }

    @Test
    @DisplayName("LA PRUEBA QUE EL FALSO NO PUEDE HACER: si la corrección falla a mitad, no queda nada aplicado")
    void correccionAtomica() {
        Variante filtro = repuestoNuevo(codigoUnico("ATO"), Dinero.de(6_000));
        Compra compra = compraDe(jotapartes(), LocalDate.of(2026, 9, 5), "FV-ATO",
                FormaPago.EFECTIVO, null, List.of(ComandoRegistrarCompra.Linea.porUnitario(
                        filtro.getId(), 10, new java.math.BigDecimal("2000"), null)));
        var detalle = consultarCompras.detalle(compra.getId(), personas.administrador()).orElseThrow();
        int movimientos = kardex.historialDe(filtro.getId()).size();

        // El renglón 1 se revierte y vuelve a entrar; el renglón nuevo apunta a un repuesto que no
        // existe y revienta. Todo tiene que deshacerse junto.
        assertThatThrownBy(() -> corregirCompra.ejecutar(correccion(detalle, List.of(
                renglonCorregido(detalle.renglones().get(0).lineaId(), filtro.getId(), 8, 2_000),
                renglonCorregido(null, UUID.randomUUID(), 1, 1_000)))))
                .hasMessageContaining("no existe");

        Variante enBase = variantes.buscarPorCodigo(filtro.getCodigo()).orElseThrow();
        assertThat(enBase.getStock()).isEqualTo(10);
        assertThat(kardex.historialDe(filtro.getId())).hasSize(movimientos);
        var sinCambios = consultarCompras.detalle(compra.getId(), personas.administrador()).orElseThrow();
        assertThat(sinCambios.renglones()).hasSize(1);
        assertThat(sinCambios.reemplazados()).isEmpty();
        assertThat(sinCambios.correcciones()).isEmpty();
        assertThat(sinCambios.version()).isEqualTo(detalle.version());
    }

    @Test
    @DisplayName("anular contra Postgres: sale del inventario, queda marcada y fuera de las vigentes")
    void anularContraPostgres() {
        Variante filtro = repuestoNuevo(codigoUnico("ANU"), Dinero.de(6_000));
        Proveedor proveedor = jotapartes();
        Compra compra = compraDe(proveedor, LocalDate.of(2026, 9, 5), "FV-ANU",
                FormaPago.EFECTIVO, null, List.of(ComandoRegistrarCompra.Linea.porUnitario(
                        filtro.getId(), 10, new java.math.BigDecimal("2000"), null)));

        anularCompra.ejecutar(compra.getId(), 0, "se registró dos veces", personas.administrador());

        var detalle = consultarCompras.detalle(compra.getId(), personas.administrador()).orElseThrow();
        assertThat(detalle.estado()).isEqualTo(EstadoCompra.ANULADA);
        assertThat(detalle.motivoAnulacion()).isEqualTo("se registró dos veces");
        assertThat(detalle.anuladaEn()).isNotNull();
        assertThat(variantes.buscarPorCodigo(filtro.getCodigo()).orElseThrow().getStock()).isZero();
        assertThat(kardex.historialDe(filtro.getId()).getLast().getTipo())
                .isEqualTo(TipoMovimiento.ANULACION_COMPRA);

        assertThat(idsDe(new FiltroCompras(proveedor.getId(), null, null, null, null, null, EstadoCompra.VIGENTE, null)))
                .as("fuera de las vigentes").isEmpty();
        assertThat(idsDe(new FiltroCompras(proveedor.getId(), null, null, null, null, null, EstadoCompra.ANULADA, null)))
                .containsExactly(compra.getId());
    }

    @Test
    @DisplayName("LA BASE exige que una compra anulada diga cuándo, quién y por qué")
    void laBaseExigeDatosDeAnulacion() {
        Variante filtro = repuestoNuevo(codigoUnico("CHK"), Dinero.de(6_000));
        Compra compra = compraDe(jotapartes(), LocalDate.of(2026, 9, 5), "FV-CHK-ANU",
                FormaPago.EFECTIVO, null, List.of(ComandoRegistrarCompra.Linea.porUnitario(
                        filtro.getId(), 1, new java.math.BigDecimal("1000"), null)));

        assertThatThrownBy(() -> jdbc.update("update compra set estado = 'ANULADA' where id = ?", compra.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("una venta posterior bloquea anular, preguntándole a Postgres por la secuencia")
    void ventaPosteriorBloqueaContraPostgres() {
        Variante filtro = repuestoNuevo(codigoUnico("VTA"), Dinero.de(6_000));
        Compra compra = compraDe(jotapartes(), LocalDate.of(2026, 9, 5), "FV-VTA",
                FormaPago.EFECTIVO, null, List.of(ComandoRegistrarCompra.Linea.porUnitario(
                        filtro.getId(), 10, new java.math.BigDecimal("2000"), null)));
        // Otra compra repone stock, así que lo único que puede bloquear es la venta en el medio.
        transaccion.executeWithoutResult(estado -> {
            Variante v = variantes.buscarParaModificar(filtro.getId()).orElseThrow();
            v.descontar(3);
            kardex.agregar(MovimientoKardex.porVenta(v, 3, UUID.randomUUID(), personas.cajero().id(),
                    java.time.Instant.now()));
            variantes.guardar(v);
        });
        compraDe(jotapartes(), LocalDate.of(2026, 9, 6), "FV-VTA-2", FormaPago.EFECTIVO, null,
                List.of(ComandoRegistrarCompra.Linea.porUnitario(filtro.getId(), 10,
                        new java.math.BigDecimal("2000"), null)));

        assertThatThrownBy(() -> anularCompra.ejecutar(compra.getId(), 0, "motivo", personas.administrador()))
                .isInstanceOfSatisfying(RenglonesBloqueadosException.class,
                        e -> assertThat(e.getCodigos()).containsExactly(filtro.getCodigo()));
        assertThat(variantes.buscarPorCodigo(filtro.getCodigo()).orElseThrow().getStock()).isEqualTo(17);
    }

    @Test
    @DisplayName("tras cambiar solo el precio, el kardex muestra el precio del renglón vigente")
    void precioPorEntradaTrasCorregir() {
        Variante filtro = repuestoNuevo(codigoUnico("PRE"), Dinero.de(6_000));
        Compra compra = compraDe(jotapartes(), LocalDate.of(2026, 9, 5), "FV-PRE",
                FormaPago.EFECTIVO, null, List.of(ComandoRegistrarCompra.Linea.porUnitario(
                        filtro.getId(), 10, new java.math.BigDecimal("2000"), Dinero.de(9_000))));
        var detalle = consultarCompras.detalle(compra.getId(), personas.administrador()).orElseThrow();

        corregirCompra.ejecutar(correccion(detalle, List.of(new ComandoCorregirCompra.Linea(
                detalle.renglones().get(0).lineaId(), ComandoRegistrarCompra.Linea.porUnitario(
                        filtro.getId(), 10, new java.math.BigDecimal("2000"), Dinero.de(12_000))))));

        List<MovimientoKardex> movimientos = kardex.historialDe(filtro.getId());
        assertThat(movimientos).as("cambiar solo el precio no mueve inventario").hasSize(1);
        var descripcion = documentosDeCompra.describir(List.of(compra.getId()),
                List.of(movimientos.get(0).getId()));
        assertThat(descripcion.precioPorEntrada()).containsEntry(movimientos.get(0).getId(), 12_000L);
        assertThat(variantes.buscarPorCodigo(filtro.getCodigo()).orElseThrow().getPrecio())
                .isEqualTo(Dinero.de(12_000));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SPEC 0002 · FASE 6 — totales, desactivar cuenta, auditoría de ficha
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("los totales de Postgres: las partes suman el total y las anuladas no cuentan")
    void totalesContraPostgres() {
        Proveedor proveedor = jotapartes();
        CuentaPago nequi = registrarCuenta.ejecutar("Nequi " + codigoUnico("TOT"), personas.administrador());
        CuentaPago banco = registrarCuenta.ejecutar("Banco " + codigoUnico("TOT"), personas.administrador());

        compraDe(proveedor, LocalDate.of(2026, 9, 1), "TOT-E1", FormaPago.EFECTIVO, null, List.of(renglon(1, 10_000)));
        compraDe(proveedor, LocalDate.of(2026, 9, 2), "TOT-E2", FormaPago.EFECTIVO, null, List.of(renglon(1, 20_000)));
        compraDe(proveedor, LocalDate.of(2026, 9, 3), "TOT-N", FormaPago.TRANSFERENCIA, nequi.getId(), List.of(renglon(1, 30_000)));
        compraDe(proveedor, LocalDate.of(2026, 9, 4), "TOT-B", FormaPago.TRANSFERENCIA, banco.getId(), List.of(renglon(1, 40_000)));
        Compra anulada = compraDe(proveedor, LocalDate.of(2026, 9, 5), "TOT-ANU", FormaPago.EFECTIVO, null,
                List.of(renglon(1, 99_000)));
        anularCompra.ejecutar(anulada.getId(), 0, "duplicada", personas.administrador());

        var totales = consultarCompras.totales(
                new FiltroCompras(proveedor.getId(), null, null, null, null, null, null, null), personas.administrador());

        assertThat(totales.total()).as("sin la anulada de $99.000").isEqualTo(Dinero.de(100_000));
        assertThat(totales.compras()).isEqualTo(4);
        // Dos consultas distintas: que cuadren no es gratis, se comprueba.
        assertThat(totales.sumaDePartes()).isEqualTo(totales.total());
        assertThat(totales.partes()).hasSize(3);
        assertThat(totales.partes()).filteredOn(p -> p.formaPago() == FormaPago.EFECTIVO)
                .singleElement().satisfies(p -> {
                    assertThat(p.cuentaId()).isNull();
                    assertThat(p.compras()).isEqualTo(2);
                    assertThat(p.total()).isEqualTo(Dinero.de(30_000));
                });
        assertThat(totales.partes()).filteredOn(p -> nequi.getId().equals(p.cuentaId()))
                .singleElement().satisfies(p -> assertThat(p.total()).isEqualTo(Dinero.de(30_000)));

        // Con un filtro, los totales son los de la lista filtrada.
        var soloSeptiembre3 = consultarCompras.totales(new FiltroCompras(proveedor.getId(),
                LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 3), null, null, null, null, null), personas.administrador());
        assertThat(soloSeptiembre3.total()).isEqualTo(Dinero.de(30_000));
        assertThat(soloSeptiembre3.sumaDePartes()).isEqualTo(soloSeptiembre3.total());
    }

    @Test
    @DisplayName("una cuenta desactivada deja de listarse, y sus compras la siguen nombrando")
    void desactivarCuentaContraPostgres() {
        CuentaPago cuenta = registrarCuenta.ejecutar("Daviplata " + codigoUnico("DES"), personas.administrador());
        Compra compra = compraDe(jotapartes(), LocalDate.of(2026, 9, 5), "FV-DES",
                FormaPago.TRANSFERENCIA, cuenta.getId(), List.of(renglon(1, 5_000)));

        desactivarCuenta.ejecutar(cuenta.getId(), personas.administrador());

        assertThat(cuentas.activas()).extracting(CuentaPago::getId).doesNotContain(cuenta.getId());
        assertThat(consultarCompras.detalle(compra.getId(), personas.administrador()).orElseThrow().cuenta()).isEqualTo(cuenta.getNombre());
    }

    @Test
    @DisplayName("corregir la ficha deja su evento en Postgres; guardarla igual no")
    void auditoriaDeFichaContraPostgres() {
        Variante repuesto = repuestoNuevo(codigoUnico("FIC"), Dinero.de(6_000));
        UUID motor = categorias.activas().stream().filter(c -> c.getNombre().equals("MOTOR")).findFirst()
                .orElseThrow().getId();
        var comando = new ActualizarRepuesto.ComandoActualizarRepuesto(
                "FILTRO CORREGIDO", motor, null, repuesto.getCodigo(), "INOKI", Dinero.de(6_000), 7,
                personas.administrador());

        actualizarRepuesto.ejecutar(repuesto.getId(), comando);
        actualizarRepuesto.ejecutar(repuesto.getId(), comando);     // mismo contenido: no deja evento

        var eventos = auditoria.historialDe(ActualizarRepuesto.TIPO_AUDITORIA, repuesto.getId());
        assertThat(eventos).singleElement().satisfies(e -> {
            assertThat(e.antes()).containsEntry("categoria", "FILTROS");
            assertThat(e.despues()).containsEntry("nombre", "FILTRO CORREGIDO").containsEntry("categoria", "MOTOR");
            assertThat(((Number) e.despues().get("stockMinimo")).intValue()).isEqualTo(7);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SPEC 0002 · FASE 7 — buscar las compras de un repuesto
    // ─────────────────────────────────────────────────────────────────────────

    private Variante repuestoConDatos(String nombre, String aplicacion, String codigo, String marca) {
        return crearRepuesto.ejecutar(ComandoCrearRepuesto.conConceptoNuevo(
                nombre, categorias.activas().getFirst().getId(), aplicacion, codigo, marca, Dinero.de(9_000), 1), personas.administrador());
    }

    private static FiltroCompras porRepuesto(String texto) {
        return new FiltroCompras(null, null, null, null, null, null, null, BusquedaDeRepuesto.de(texto));
    }

    private static ComandoRegistrarCompra.Linea de(Variante repuesto, int cantidad, long total) {
        return ComandoRegistrarCompra.Linea.porTotal(repuesto.getId(), cantidad, Dinero.de(total), null);
    }

    @Test
    @DisplayName("buscar por repuesto en Postgres: por cada campo, sin repetir facturas, solo renglones vigentes, y % tal cual")
    void buscarPorRepuestoContraPostgres() {
        // Un sufijo propio en cada campo: el contenedor se comparte y otras pruebas dejan compras.
        String s = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Variante bujia = repuestoConDatos("BUJÍA ÑANDÚ " + s, "PULSAR ZETA " + s, "BUJ-" + s, "NGK " + s);
        Variante cadena = repuestoConDatos("CADENA " + s, "BOXER " + s, "CAD-" + s, "DID " + s);
        Variante otro = repuestoConDatos("TORNILLO " + s, null, "TOR-" + s, "GENERICO " + s);
        Proveedor proveedor = jotapartes();

        Compra lasDos = compraDe(proveedor, LocalDate.of(2026, 9, 10), "BUS-A-" + s, FormaPago.EFECTIVO, null,
                List.of(de(bujia, 2, 18_000), de(cadena, 1, 50_000)));
        Compra soloCadena = compraDe(proveedor, LocalDate.of(2026, 9, 11), "BUS-B-" + s, FormaPago.EFECTIVO, null,
                List.of(de(cadena, 1, 52_000)));
        Compra bujiaQuitada = compraDe(proveedor, LocalDate.of(2026, 9, 12), "BUS-C-" + s, FormaPago.EFECTIVO, null,
                List.of(de(bujia, 1, 9_000), de(otro, 3, 3_000)));
        var antes = consultarCompras.detalle(bujiaQuitada.getId(), personas.administrador()).orElseThrow();
        var renglonOtro = antes.renglones().stream()
                .filter(r -> r.repuesto().id().equals(otro.getId())).findFirst().orElseThrow();
        corregirCompra.ejecutar(correccion(antes, List.of(
                renglonCorregido(renglonOtro.lineaId(), otro.getId(), 3, 1_000))));

        String minusculas = s.toLowerCase();
        assertThat(idsDe(porRepuesto("buj-" + minusculas))).as("código")
                .containsExactly(lasDos.getId());
        assertThat(idsDe(porRepuesto("bujía ñandú " + minusculas))).as("nombre, con tilde y eñe en minúscula")
                .containsExactly(lasDos.getId());
        assertThat(idsDe(porRepuesto("bujia nandu " + minusculas))).as("nombre, sin tildes ni eñe")
                .containsExactly(lasDos.getId());
        assertThat(idsDe(porRepuesto("ngk " + minusculas))).as("marca").containsExactly(lasDos.getId());
        assertThat(idsDe(porRepuesto("PULSAR zeta " + s))).as("aplicación").containsExactly(lasDos.getId());

        // El sufijo está en los dos renglones de lasDos: sale una sola vez, y el conteo lo respeta.
        var todas = consultarCompras.historial(porRepuesto(s), 0, 25, personas.administrador());
        assertThat(todas.elementos()).extracting(r -> r.id())
                .containsExactlyInAnyOrder(lasDos.getId(), soloCadena.getId(), bujiaQuitada.getId());
        assertThat(todas.total()).isEqualTo(3);
        var totales = consultarCompras.totales(porRepuesto("cad-" + minusculas), personas.administrador());
        assertThat(totales.compras()).isEqualTo(2);
        assertThat(totales.total()).as("facturas completas").isEqualTo(Dinero.de(68_000 + 52_000));

        // El usuario escribe comodines: se buscan tal cual.
        assertThat(idsDe(porRepuesto("buj_" + minusculas))).as("_ no vale por cualquier letra").isEmpty();
        assertThat(idsDe(porRepuesto("%" + minusculas))).as("% no trae todo").isEmpty();
        assertThat(idsDe(porRepuesto("!" + minusculas))).as("! es el escape y se busca tal cual").isEmpty();
    }

    @Test
    @DisplayName("LA LISTA Y EL DETALLE ESTÁN DE ACUERDO: sale en la lista si y solo si al abrirla hay algo resaltado")
    void listaYDetalleDeAcuerdo() {
        String s = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Variante bujia = repuestoConDatos("BUJÍA ÑANDÚ " + s, "PULSAR ZETA " + s, "BUJ-" + s, "NGK " + s);
        Variante cadena = repuestoConDatos("CADENA " + s, "BOXER " + s, "CAD-" + s, "DID " + s);
        Proveedor proveedor = jotapartes();
        Compra lasDos = compraDe(proveedor, LocalDate.of(2026, 9, 10), "ACU-A-" + s, FormaPago.EFECTIVO, null,
                List.of(de(bujia, 2, 18_000), de(cadena, 1, 50_000)));
        Compra soloCadena = compraDe(proveedor, LocalDate.of(2026, 9, 11), "ACU-B-" + s, FormaPago.EFECTIVO, null,
                List.of(de(cadena, 1, 52_000)));
        Compra conCorreccion = compraDe(proveedor, LocalDate.of(2026, 9, 12), "ACU-C-" + s, FormaPago.EFECTIVO, null,
                List.of(de(bujia, 4, 36_000)));
        // La bujía se corrige a cadena en el mismo renglón: la versión vieja sigue siendo de bujía.
        var antes = consultarCompras.detalle(conCorreccion.getId(), personas.administrador()).orElseThrow();
        corregirCompra.ejecutar(correccion(antes, List.of(
                renglonCorregido(antes.renglones().get(0).lineaId(), cadena.getId(), 4, 9_000))));

        String m = s.toLowerCase();
        List<String> busquedas = List.of("buj-" + m, "BUJÍA ñandú " + s, "bujia nandu " + m, "ÑANDU " + s,
                "ngk " + m, "pulsar zeta " + m,
                "cad-" + m, "boxer " + m, "did " + m, s, "no-existe-" + m);
        for (String busqueda : busquedas) {
            List<UUID> enLista = idsDe(porRepuesto(busqueda));
            for (Compra compra : List.of(lasDos, soloCadena, conCorreccion)) {
                var detalle = consultarCompras.detalle(compra.getId(), BusquedaDeRepuesto.de(busqueda), personas.administrador()).orElseThrow();
                boolean resaltada = detalle.renglones().stream().anyMatch(DetalleCompra.Renglon::coincide);
                assertThat(resaltada)
                        .as("«%s» en %s: en la lista=%s", busqueda, compra.getNumeroFactura(), enLista.contains(compra.getId()))
                        .isEqualTo(enLista.contains(compra.getId()));
                assertThat(detalle.reemplazados()).noneMatch(DetalleCompra.Renglon::coincide);
            }

            // RF-027: lo que la lista muestra debajo de cada factura son exactamente los renglones que
            // el detalle resalta al abrirla. Ni uno de más (un renglón viejo de la corrección) ni de menos.
            for (FilaHistorial fila : consultarCompras.historialConCoincidencias(porRepuesto(busqueda), 0, 25, personas.administrador()).elementos()) {
                var detalle = consultarCompras.detalle(fila.resumen().id(), BusquedaDeRepuesto.de(busqueda), personas.administrador()).orElseThrow();
                assertThat(fila.coinciden()).extracting(FilaHistorial.RenglonQueCoincide::lineaId)
                        .as("«%s» en %s: lo de la lista es lo resaltado", busqueda, fila.resumen().numeroFactura())
                        .containsExactlyElementsOf(detalle.renglones().stream()
                                .filter(DetalleCompra.Renglon::coincide).map(DetalleCompra.Renglon::lineaId).toList())
                        .isNotEmpty();
            }
        }

        // RF-028: sin buscar, debajo de cada factura van sus primeros renglones vigentes, en el orden del
        // detalle. La de la corrección muestra la cadena, no la bujía que se reemplazó.
        var sinBuscar = consultarCompras.historialConCoincidencias(
                new FiltroCompras(null, null, null, null, null, "-" + s, null, null), 0, 25, personas.administrador()).elementos();
        assertThat(sinBuscar).extracting(f -> f.resumen().id())
                .containsExactlyInAnyOrder(lasDos.getId(), soloCadena.getId(), conCorreccion.getId());
        for (FilaHistorial fila : sinBuscar) {
            var detalle = consultarCompras.detalle(fila.resumen().id(), personas.administrador()).orElseThrow();
            assertThat(fila.primeros()).extracting(FilaHistorial.RenglonQueCoincide::lineaId)
                    .as("los primeros de %s", fila.resumen().numeroFactura())
                    .containsExactlyElementsOf(detalle.renglones().stream().limit(FilaHistorial.MAXIMO_PRIMEROS)
                            .map(DetalleCompra.Renglon::lineaId).toList());
            assertThat(fila.coinciden()).isEmpty();
        }
        assertThat(sinBuscar).filteredOn(f -> f.resumen().id().equals(conCorreccion.getId())).singleElement()
                .satisfies(f -> assertThat(f.primeros()).singleElement()
                        .satisfies(r -> assertThat(r.codigo()).isEqualTo(cadena.getCodigo())));
    }

    @Test
    @DisplayName("DOS REGISTROS A LA VEZ con la misma llave: entra una compra y el stock sube una sola vez (spec 0009, RF-009)")
    void dosComprasALaVezConLaMismaLlave() throws Exception {
        String codigo = codigoUnico("LLV");
        Variante repuesto = repuestoNuevo(codigo, Dinero.de(19_000));
        UUID llave = UUID.randomUUID();
        Proveedor proveedor = jotapartes();
        Callable<Compra> registrar = () -> registrarCompra.ejecutar(new ComandoRegistrarCompra(
                proveedor.getId(), LocalDate.of(2026, 9, 21), "FV-LLAVE", FormaPago.EFECTIVO, null,
                personas.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porUnitario(
                        repuesto.getId(), 10, new java.math.BigDecimal("1000"), null)),
                false, llave));

        List<Object> resultados = aLaVez(List.of(registrar, registrar));

        assertThat(resultados).allSatisfy(r -> assertThat(r).isInstanceOf(Compra.class));
        assertThat(resultados.stream().map(r -> ((Compra) r).getId()).distinct())
                .as("las dos respuestas son la misma compra").hasSize(1);
        Variante desdeLaBase = variantes.buscarPorCodigo(codigo).orElseThrow();
        assertThat(desdeLaBase.getStock()).as("la mercancía entra una sola vez").isEqualTo(10);
        assertThat(desdeLaBase.getCostoPromedio()).isEqualByComparingTo("1000.0000");
        assertThat(jdbc.queryForObject("select count(*) from compra where llave_idempotencia = ?", Integer.class, llave))
                .isEqualTo(1);
    }

    /** Las dos tareas salen a la vez y se espera a las dos; devuelve lo que dio cada una, o su excepción. */
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
                } catch (ExecutionException e) {
                    resultados.add(e.getCause());
                }
            }
            return resultados;
        } finally {
            hilos.shutdownNow();
        }
    }

    @Test
    @DisplayName("TODOS LOS BUSCADORES IGNORAN TILDES en Postgres: inventario, búsqueda por texto y sugerencias de concepto")
    void buscadoresIgnoranTildesContraPostgres() {
        String s = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Variante conTildes = repuestoConDatos("BUJÍA ÑANDÚ " + s, "MOTO ÁGUILA " + s, "TIL-" + s, "MARCÁ " + s);
        Variante sinTildes = repuestoConDatos("CANCION " + s, null, "SIN-" + s, "PINGUINO " + s);
        String m = s.toLowerCase();

        assertThat(buscarRepuestos.inventario("bujia nandu " + m, false, 0, 10).elementos())
                .as("inventario: sin tildes encuentra con tildes")
                .extracting(RepuestoEncontrado::id).containsExactly(conTildes.getId());
        assertThat(buscarRepuestos.inventario("canción " + m, false, 0, 10).elementos())
                .as("inventario: con tilde encuentra sin tilde")
                .extracting(RepuestoEncontrado::id).containsExactly(sinTildes.getId());
        assertThat(buscarRepuestos.inventario("marca " + m, false, 0, 10).total()).as("el conteo también").isEqualTo(1);
        assertThat(buscarRepuestos.porTexto("aguila " + m)).as("búsqueda por texto, por la aplicación")
                .extracting(RepuestoEncontrado::id).containsExactly(conTildes.getId());
        assertThat(productos.buscarPorNombre("BUJIA NANDU " + s)).as("sugerencias: BUJIA ofrece el BUJÍA que ya existe")
                .extracting(Producto::getNombre).containsExactly("BUJÍA ÑANDÚ " + s);
    }
}

