package com.workshopmanagement.rdmotors.compras.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compras.dominio.BusquedaDeRepuesto;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;
import com.workshopmanagement.rdmotors.compras.dominio.FiltroCompras;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compras.dominio.LineaCompra;
import com.workshopmanagement.rdmotors.compras.dominio.ModoCaptura;
import com.workshopmanagement.rdmotors.compras.dominio.Proveedor;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/**
 * El historial y el detalle. Aquí se prueba lo que es del caso de uso —sanear lo que llega por URL,
 * validar el rango de fechas, armar el detalle—; que la consulta filtre bien contra Postgres lo
 * prueba la integración.
 */
class ConsultarComprasTest {

    private final Actor admin = ActoresDePrueba.administrador();

    private Falsos.ComprasEnMemoria compras;
    private ConsultarCompras consultar;

    private Proveedor jotapartes;
    private Variante filtro;
    private Variante pastillas;

    @BeforeEach
    void preparar() {
        compras = new Falsos.ComprasEnMemoria();
        consultar = new ConsultarCompras(compras, new Falsos.AuditoriaEnMemoria(), new Falsos.UsuariosEnMemoria());

        jotapartes = Proveedor.nuevo("Importadora Jotapartes", null, null);
        filtro = Variante.nueva(Producto.nuevo("FILTRO ACEITE", Categoria.nueva("PRUEBAS", 99), null),
                "352B59K", "INOKI", Dinero.de(6_000), 5);
        pastillas = Variante.nueva(Producto.nuevo("PASTILLAS FRENO", Categoria.nueva("PRUEBAS", 99), null),
                "152RTX2B", "CBI", Dinero.de(24_000), 4);
    }

    private Compra compra(LocalDate fecha, String factura, FormaPago forma, CuentaPago cuenta,
                          LineaCompra... lineas) {
        return compras.guardar(Compra.registrar(jotapartes, fecha, Instant.parse("2026-09-13T15:00:00Z"),
                factura, forma, cuenta, UUID.randomUUID(), List.of(lineas)));
    }

    private LineaCompra lineaDeFiltro() {
        return LineaCompra.porTotal(filtro, 15, Dinero.de(200_000), null);
    }

    // ── Historial ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("de la factura más reciente a la más antigua, por fecha de la factura")
    void ordenadoPorFechaDeFactura() {
        compra(LocalDate.of(2026, 8, 13), "FV-AGOSTO", FormaPago.EFECTIVO, null, lineaDeFiltro());
        compra(LocalDate.of(2026, 9, 5), "FV-SEPTIEMBRE", FormaPago.EFECTIVO, null, lineaDeFiltro());

        assertThat(consultar.historial(FiltroCompras.sinFiltros(), 0, 25, admin).elementos())
                .extracting(r -> r.numeroFactura())
                .containsExactly("FV-SEPTIEMBRE", "FV-AGOSTO");
    }

    @Test
    @DisplayName("la fila dice forma de pago, cuenta, renglones y total")
    void laFilaTraeLoQueSeVe() {
        CuentaPago nequi = CuentaPago.nueva("Nequi del dueño");
        compra(LocalDate.of(2026, 9, 5), "FV-9912", FormaPago.TRANSFERENCIA, nequi,
                lineaDeFiltro(),
                LineaCompra.porUnitario(pastillas, 6, new java.math.BigDecimal("14200"), null));

        var fila = consultar.historial(FiltroCompras.sinFiltros(), 0, 25, admin).elementos().get(0);

        assertThat(fila.formaPago()).isEqualTo(FormaPago.TRANSFERENCIA);
        assertThat(fila.cuenta()).isEqualTo("Nequi del dueño");
        assertThat(fila.renglones()).isEqualTo(2);
        assertThat(fila.total()).isEqualTo(Dinero.de(200_000 + 6 * 14_200));
    }

    @Test
    @DisplayName("un rango de fechas al revés se rechaza con un mensaje legible")
    void rangoDeFechasAlReves() {
        assertThatThrownBy(() -> new FiltroCompras(null, LocalDate.of(2026, 9, 30),
                LocalDate.of(2026, 9, 1), null, null, null, null, null))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("posterior");
    }

    @Test
    @DisplayName("una factura en blanco no filtra: se busca como si no hubiera escrito nada")
    void facturaEnBlancoNoFiltra() {
        assertThat(new FiltroCompras(null, null, null, null, null, "   ", null, null).factura()).isNull();
        assertThat(new FiltroCompras(null, null, null, null, null, " fv-99 ", null, null).factura()).isEqualTo("fv-99");
    }

    @Test
    @DisplayName("parámetros absurdos de la URL se sanean en vez de reventar")
    void saneaLaPagina() {
        compra(LocalDate.of(2026, 9, 5), "FV-1", FormaPago.EFECTIVO, null, lineaDeFiltro());

        var pagina = consultar.historial(null, -4, 100_000, admin);

        assertThat(pagina.numero()).isZero();
        assertThat(pagina.tamano()).isEqualTo(ConsultarCompras.TAMANO_MAXIMO_PAGINA);
        assertThat(pagina.total()).isEqualTo(1);
        assertThat(consultar.historial(FiltroCompras.sinFiltros(), 0, 0, admin).tamano()).isEqualTo(1);
    }

    // ── Detalle ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("el detalle trae cada renglón con lo que se registró y el repuesto como está hoy")
    void detalleCompleto() {
        Compra registrada = compra(LocalDate.of(2026, 9, 5), "FV-9912", FormaPago.EFECTIVO, null,
                lineaDeFiltro(),
                LineaCompra.porUnitario(pastillas, 6, new java.math.BigDecimal("14200"),
                        Dinero.de(30_000)));

        DetalleCompra detalle = consultar.detalle(registrada.getId(), admin).orElseThrow();

        assertThat(detalle.proveedor()).isEqualTo("Importadora Jotapartes");
        assertThat(detalle.formaPago()).isEqualTo(FormaPago.EFECTIVO);
        assertThat(detalle.cuenta()).isNull();
        assertThat(detalle.renglones()).hasSize(2);

        var primero = detalle.renglones().get(0);
        assertThat(primero.modoCaptura()).isEqualTo(ModoCaptura.TOTAL);
        assertThat(primero.costoUnitario()).isEqualByComparingTo("13333.3333");
        assertThat(primero.precioVenta()).as("esa compra no cambió el precio").isNull();
        assertThat(primero.repuesto().codigo()).isEqualTo("352B59K");

        assertThat(detalle.renglones().get(1).precioVenta()).isEqualTo(Dinero.de(30_000));
    }

    @Test
    @DisplayName("el total del detalle cuadra al peso con la suma de sus renglones")
    void totalCuadraConRenglones() {
        Compra registrada = compra(LocalDate.of(2026, 9, 5), "FV-9912", FormaPago.EFECTIVO, null,
                lineaDeFiltro(),
                LineaCompra.porUnitario(pastillas, 6, new java.math.BigDecimal("14200"), null));

        DetalleCompra detalle = consultar.detalle(registrada.getId(), admin).orElseThrow();

        Dinero suma = detalle.renglones().stream()
                .map(DetalleCompra.Renglon::costoTotal)
                .reduce(Dinero.CERO, Dinero::mas);
        assertThat(detalle.total()).isEqualTo(suma).isEqualTo(Dinero.de(285_200));
    }

    @Test
    @DisplayName("una compra que no existe devuelve vacío, no un error")
    void detalleInexistente() {
        assertThat(consultar.detalle(UUID.randomUUID(), admin)).isEmpty();
    }

    // ── Totales (spec 0002, H6) ──────────────────────────────────────────────

    @Test
    @DisplayName("los totales separan efectivo de cada cuenta, y las partes suman el total")
    void totalesPorFormaDePago() {
        CuentaPago nequi = CuentaPago.nueva("Nequi del dueño");
        CuentaPago banco = CuentaPago.nueva("Bancolombia ···4521");
        compra(LocalDate.of(2026, 9, 1), "E-1", FormaPago.EFECTIVO, null, lineaDeFiltro());
        compra(LocalDate.of(2026, 9, 2), "E-2", FormaPago.EFECTIVO, null, lineaDeFiltro());
        compra(LocalDate.of(2026, 9, 3), "N-1", FormaPago.TRANSFERENCIA, nequi, lineaDeFiltro());
        compra(LocalDate.of(2026, 9, 4), "B-1", FormaPago.TRANSFERENCIA, banco, lineaDeFiltro());

        var totales = consultar.totales(FiltroCompras.sinFiltros(), admin);

        assertThat(totales.total()).isEqualTo(Dinero.de(800_000));
        assertThat(totales.compras()).isEqualTo(4);
        assertThat(totales.sumaDePartes()).isEqualTo(totales.total());
        assertThat(totales.partes()).hasSize(3);
        assertThat(totales.partes()).filteredOn(p -> p.formaPago() == FormaPago.EFECTIVO)
                .singleElement().satisfies(p -> {
                    assertThat(p.compras()).isEqualTo(2);
                    assertThat(p.total()).isEqualTo(Dinero.de(400_000));
                });
    }

    @Test
    @DisplayName("las anuladas no cuentan en los totales, aunque el filtro pida anuladas")
    void anuladasNoCuentan() {
        compra(LocalDate.of(2026, 9, 1), "VIGENTE", FormaPago.EFECTIVO, null, lineaDeFiltro());
        Compra anulada = compra(LocalDate.of(2026, 9, 2), "ANULADA", FormaPago.EFECTIVO, null, lineaDeFiltro());
        anulada.anular("duplicada", UUID.randomUUID(), Instant.parse("2026-09-13T16:00:00Z"));

        assertThat(consultar.totales(FiltroCompras.sinFiltros(), admin).total()).isEqualTo(Dinero.de(200_000));
        assertThat(consultar.totales(new FiltroCompras(null, null, null, null, null, null,
                com.workshopmanagement.rdmotors.compras.dominio.EstadoCompra.ANULADA, null), admin).compras())
                .isZero();
    }

    // ── Buscar por repuesto (spec 0002, H9) ──────────────────────────────────

    private static FiltroCompras porRepuesto(String texto) {
        return new FiltroCompras(null, null, null, null, null, null, null, BusquedaDeRepuesto.de(texto));
    }

    private LineaCompra lineaDePastillas() {
        return LineaCompra.porUnitario(pastillas, 6, new java.math.BigDecimal("14200"), null);
    }

    @Test
    @DisplayName("buscar un repuesto deja las compras donde vino, y los totales son los de esas compras")
    void filtraPorRepuesto() {
        compra(LocalDate.of(2026, 9, 1), "CON-FILTRO", FormaPago.EFECTIVO, null, lineaDeFiltro());
        compra(LocalDate.of(2026, 9, 2), "SOLO-PASTILLAS", FormaPago.EFECTIVO, null, lineaDePastillas());
        compra(LocalDate.of(2026, 9, 3), "LAS-DOS", FormaPago.EFECTIVO, null, lineaDeFiltro(), lineaDePastillas());

        assertThat(consultar.historial(porRepuesto("inoki"), 0, 25, admin).elementos())
                .extracting(r -> r.numeroFactura())
                .containsExactly("LAS-DOS", "CON-FILTRO");
        // Las facturas completas: LAS-DOS suma también sus pastillas (decidido en la revisión).
        assertThat(consultar.totales(porRepuesto("inoki"), admin).total())
                .isEqualTo(Dinero.de(200_000 + 200_000 + 6 * 14_200));
    }

    @Test
    @DisplayName("un renglón que una corrección quitó ya no hace salir la compra")
    void renglonNoVigenteNoCuenta() {
        LineaCompra filtroQuitado = lineaDeFiltro();
        Compra compra = compra(LocalDate.of(2026, 9, 1), "FV-1", FormaPago.EFECTIVO, null,
                filtroQuitado, lineaDePastillas());
        compra.quitarLinea(filtroQuitado, Instant.parse("2026-09-13T16:00:00Z"));

        assertThat(consultar.historial(porRepuesto("inoki"), 0, 25, admin).elementos()).isEmpty();
        assertThat(consultar.historial(porRepuesto("cbi"), 0, 25, admin).elementos()).hasSize(1);
    }

    @Test
    @DisplayName("el detalle marca solo los renglones vigentes que coinciden, y sin búsqueda ninguno")
    void detalleMarcaLoQueCoincide() {
        LineaCompra filtroViejo = lineaDeFiltro();
        Compra compra = compra(LocalDate.of(2026, 9, 1), "FV-1", FormaPago.EFECTIVO, null,
                filtroViejo, lineaDePastillas());
        // Una corrección cambió el filtro por otra versión: la vieja queda como historia.
        compra.reemplazarLinea(filtroViejo, LineaCompra.porTotal(filtro, 10, Dinero.de(130_000), null),
                Instant.parse("2026-09-13T16:00:00Z"));

        var detalle = consultar.detalle(compra.getId(), BusquedaDeRepuesto.de("INOKI"), admin).orElseThrow();

        assertThat(detalle.renglones())
                .extracting(r -> r.repuesto().codigo(), DetalleCompra.Renglon::coincide)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("352B59K", true),
                        org.assertj.core.groups.Tuple.tuple("152RTX2B", false));
        assertThat(detalle.reemplazados()).as("lo de antes de corregir no se resalta")
                .singleElement().satisfies(r -> assertThat(r.coincide()).isFalse());
        assertThat(consultar.detalle(compra.getId(), admin).orElseThrow().renglones())
                .noneMatch(DetalleCompra.Renglon::coincide);
    }

    // ── Lo que coincide, en la lista (spec 0002, RF-027) ─────────────────────

    @Test
    @DisplayName("RF-027: cada factura de la lista trae sus renglones que coinciden, con la cantidad, en el orden de la factura")
    void listaTraeLoQueCoincide() {
        Variante filtroFactory = Variante.nueva(filtro.getProducto(), "370P2NN", "FACTORY", Dinero.de(5_000), 5);
        compra(LocalDate.of(2026, 9, 1), "CON-FILTRO", FormaPago.EFECTIVO, null, lineaDeFiltro());
        compra(LocalDate.of(2026, 9, 3), "LAS-DOS", FormaPago.EFECTIVO, null,
                LineaCompra.porTotal(filtroFactory, 5, Dinero.de(50_000), null), lineaDePastillas(), lineaDeFiltro());

        var filas = consultar.historialConCoincidencias(porRepuesto("aceite"), 0, 25, admin).elementos();

        assertThat(filas).extracting(f -> f.resumen().numeroFactura()).containsExactly("LAS-DOS", "CON-FILTRO");
        assertThat(filas.get(0).coinciden())
                .as("las pastillas no tienen aceite; los dos filtros sí, en el orden de la factura")
                .extracting(FilaHistorial.RenglonQueCoincide::codigo, FilaHistorial.RenglonQueCoincide::cantidad)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("370P2NN", 5),
                        org.assertj.core.groups.Tuple.tuple("352B59K", 15));
        assertThat(filas.get(1).coinciden()).singleElement().satisfies(r -> {
            assertThat(r.nombre()).isEqualTo("FILTRO ACEITE");
            assertThat(r.marca()).isEqualTo("INOKI");
        });
    }

    @Test
    @DisplayName("RF-027: un renglón que una corrección cambió no aparece debajo de la factura; el vigente sí")
    void listaSinRenglonesNoVigentes() {
        LineaCompra filtroViejo = lineaDeFiltro();
        Compra compra = compra(LocalDate.of(2026, 9, 1), "FV-1", FormaPago.EFECTIVO, null, filtroViejo);
        LineaCompra filtroNuevo = LineaCompra.porTotal(filtro, 10, Dinero.de(130_000), null);
        compra.reemplazarLinea(filtroViejo, filtroNuevo, Instant.parse("2026-09-13T16:00:00Z"));

        var fila = consultar.historialConCoincidencias(porRepuesto("inoki"), 0, 25, admin).elementos().getFirst();

        assertThat(fila.coinciden()).singleElement()
                .satisfies(r -> assertThat(r.lineaId()).isEqualTo(filtroNuevo.getId()))
                .satisfies(r -> assertThat(r.cantidad()).isEqualTo(10));
    }

    // ── Sin buscar, lo que trae cada factura (spec 0002, RF-028) ─────────────

    @Test
    @DisplayName("RF-028: sin buscar, cada factura trae sus primeros tres renglones vigentes en orden, y nada que coincida; una sola consulta de renglones")
    void sinBuscarTraeLosPrimeros() {
        Variante bujia = Variante.nueva(filtro.getProducto(), "BUJ-1", "NGK", Dinero.de(8_000), 5);
        Variante cadena = Variante.nueva(filtro.getProducto(), "CAD-1", "DID", Dinero.de(90_000), 5);
        compra(LocalDate.of(2026, 9, 1), "CUATRO", FormaPago.EFECTIVO, null, lineaDeFiltro(), lineaDePastillas(),
                LineaCompra.porTotal(bujia, 10, Dinero.de(40_000), null),
                LineaCompra.porTotal(cadena, 1, Dinero.de(60_000), null));
        compra(LocalDate.of(2026, 9, 2), "UNA", FormaPago.EFECTIVO, null, lineaDePastillas());

        var filas = consultar.historialConCoincidencias(FiltroCompras.sinFiltros(), 0, 25, admin).elementos();

        assertThat(filas).extracting(f -> f.resumen().numeroFactura()).containsExactly("UNA", "CUATRO");
        assertThat(filas.get(1).primeros())
                .extracting(FilaHistorial.RenglonQueCoincide::codigo, FilaHistorial.RenglonQueCoincide::cantidad)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("352B59K", 15),
                        org.assertj.core.groups.Tuple.tuple("152RTX2B", 6),
                        org.assertj.core.groups.Tuple.tuple("BUJ-1", 10));
        assertThat(filas.get(1).resumen().renglones()).as("la pantalla dice 'y 1 más'").isEqualTo(4);
        assertThat(filas.get(0).primeros()).hasSize(1);
        assertThat(filas).allSatisfy(f -> assertThat(f.coinciden()).isEmpty());
        assertThat(compras.vecesPedidasLineasVigentes).as("una sola consulta para toda la página").isEqualTo(1);
        assertThat(consultar.historialConCoincidencias(null, 0, 25, admin).elementos()).hasSize(2);
    }

    @Test
    @DisplayName("RF-028: un renglón que una corrección cambió no sale entre los primeros; buscando, trae los primeros y lo que coincide")
    void primerosSinRenglonesNoVigentes() {
        LineaCompra filtroViejo = lineaDeFiltro();
        Compra compra = compra(LocalDate.of(2026, 9, 1), "FV-1", FormaPago.EFECTIVO, null, filtroViejo,
                lineaDePastillas());
        LineaCompra filtroNuevo = LineaCompra.porTotal(filtro, 10, Dinero.de(130_000), null);
        compra.reemplazarLinea(filtroViejo, filtroNuevo, Instant.parse("2026-09-13T16:00:00Z"));

        FilaHistorial sinBuscar = consultar.historialConCoincidencias(FiltroCompras.sinFiltros(), 0, 25, admin)
                .elementos().getFirst();
        FilaHistorial buscando = consultar.historialConCoincidencias(porRepuesto("inoki"), 0, 25, admin)
                .elementos().getFirst();

        assertThat(sinBuscar.primeros()).extracting(FilaHistorial.RenglonQueCoincide::lineaId)
                .doesNotContain(filtroViejo.getId()).contains(filtroNuevo.getId()).hasSize(2);
        assertThat(buscando.primeros()).hasSize(2);
        assertThat(buscando.coinciden()).singleElement()
                .satisfies(r -> assertThat(r.lineaId()).isEqualTo(filtroNuevo.getId()));
    }

    @Test
    @DisplayName("una página sin facturas no pide renglones")
    void paginaVaciaNoPideRenglones() {
        assertThat(consultar.historialConCoincidencias(FiltroCompras.sinFiltros(), 0, 25, admin).elementos()).isEmpty();
        assertThat(compras.vecesPedidasLineasVigentes).isZero();
    }

    @Test
    @DisplayName("cambiar el estado de un filtro conserva la búsqueda de repuesto")
    void conEstadoConservaLaBusqueda() {
        FiltroCompras filtro = porRepuesto("inoki");

        assertThat(filtro.conEstado(com.workshopmanagement.rdmotors.compras.dominio.EstadoCompra.VIGENTE))
                .isEqualTo(new FiltroCompras(null, null, null, null, null, null,
                        com.workshopmanagement.rdmotors.compras.dominio.EstadoCompra.VIGENTE,
                        BusquedaDeRepuesto.de("inoki")));
    }
}

