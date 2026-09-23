package com.workshopmanagement.rdmotors.compras.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compras.dominio.ModoCaptura;
import com.workshopmanagement.rdmotors.compras.dominio.Proveedor;
import com.workshopmanagement.rdmotors.inventario.dominio.MovimientoKardex;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ComandoCrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.aplicacion.CrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.CodigoDuplicadoException;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.TipoMovimiento;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/**
 * El caso de uso completo, <b>sin base de datos, sin Spring, sin Docker</b>.
 *
 * <p>Todo lo que necesita es construir cinco falsos en memoria. Esa es la razon practica por la
 * que este proyecto es hexagonal: en un sistema donde la correccion del inventario y de la caja
 * ES el producto, las pruebas de plata tienen que ser lo bastante baratas como para escribirlas.
 */
class RegistrarCompraTest {

    private Falsos.VariantesEnMemoria variantes;
    private Falsos.ProveedoresEnMemoria proveedores;
    private Falsos.CuentasEnMemoria cuentas;
    private Falsos.ComprasEnMemoria compras;
    private Falsos.KardexEnMemoria kardex;
    private Falsos.ProductosEnMemoria productos;
    private Falsos.CategoriasEnMemoria categorias;
    private RegistrarCompra registrarCompra;

    private Proveedor jotapartes;
    private Variante filtro;

    @BeforeEach
    void preparar() {
        variantes = new Falsos.VariantesEnMemoria();
        proveedores = new Falsos.ProveedoresEnMemoria();
        // Spec 0002, fase 1: el caso de uso gana las cuentas. Otra vez cambia el cableado y el
        // helper registrar(...), no las aserciones.
        cuentas = new Falsos.CuentasEnMemoria();
        compras = new Falsos.ComprasEnMemoria();
        kardex = new Falsos.KardexEnMemoria();
        // El caso de uso gana una dependencia (fase 4: crear el repuesto durante la compra).
        // Esta línea de cableado cambia; NINGUNA de las 14 pruebas de abajo se tocó.
        productos = new Falsos.ProductosEnMemoria();
        categorias = new Falsos.CategoriasEnMemoria();
        registrarCompra = new RegistrarCompra(compras, proveedores, cuentas, new Falsos.TurnosEnMemoria(), variantes, kardex,
                new CrearRepuesto(variantes, productos, categorias),
                new Falsos.RelojFijo("2026-09-09T14:30:00Z"));

        jotapartes = proveedores.sembrar(
                Proveedor.nuevo("Importadora Jotapartes", "900123456-7", "3001234567"));

        // El concepto se siembra en LOS DOS repositorios. Los dobles en memoria son mapas
        // independientes y permiten sembrar una variante cuyo concepto no existe — algo que
        // Postgres jamás permitiría por la clave foránea. Es el límite de los dobles, y la
        // razón de que la prueba de integración también exista.
        Producto producto = productos.sembrar(Producto.nuevo("FILTRO ACEITE", Categoria.nueva("PRUEBAS", 99),
                "PULSAR NS 200/FI/AS 200-DUKE 200"));
        filtro = variantes.sembrar(
                Variante.nueva(producto, "352B59K", "INOKI", Dinero.de(6_000), 5));
    }

    private Compra registrar(ComandoRegistrarCompra.Linea... lineas) {
        return registrarCompra.ejecutar(new ComandoRegistrarCompra(
                jotapartes.getId(), LocalDate.of(2026, 8, 13), "FV-9912",
                FormaPago.EFECTIVO, null, ActoresDePrueba.administrador(), List.of(lineas)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MODO LOTE — lo que el car-wash no sabe hacer
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("modo TOTAL: 'me llegaron 15 y pague $200.000' — y no se pierde ni un peso")
    void modoTotalNoPierdePlata() {
        Compra compra = registrar(
                ComandoRegistrarCompra.Linea.porTotal(filtro.getId(), 15, Dinero.de(200_000), null));

        var linea = compra.getLineas().get(0);

        assertThat(linea.getModoCaptura()).isEqualTo(ModoCaptura.TOTAL);
        assertThat(linea.getCostoTotal()).isEqualTo(Dinero.de(200_000));      // el hecho, exacto
        assertThat(linea.getCostoUnitario()).isEqualByComparingTo("13333.3333"); // derivado

        // Y lo que importa: reconstruir el total desde el unitario cuadra con la factura.
        assertThat(Dinero.desdeUnitario(linea.getCostoUnitario(), 15)).isEqualTo(Dinero.de(200_000));
        assertThat(compra.getTotal()).isEqualTo(Dinero.de(200_000));
    }

    @Test
    @DisplayName("modo UNITARIO: 'me llegaron 20 a $10.000 cada uno'")
    void modoUnitario() {
        Compra compra = registrar(ComandoRegistrarCompra.Linea.porUnitario(
                filtro.getId(), 20, new BigDecimal("10000"), null));

        var linea = compra.getLineas().get(0);
        assertThat(linea.getModoCaptura()).isEqualTo(ModoCaptura.UNITARIO);
        assertThat(linea.getCostoTotal()).isEqualTo(Dinero.de(200_000));
        assertThat(linea.getCostoUnitario()).isEqualByComparingTo("10000.0000");
    }

    @Test
    @DisplayName("una factura mezcla los dos modos y el total sigue cuadrando")
    void modosMezcladosEnLaMismaFactura() {
        Producto p2 = Producto.nuevo("PASTILLAS FRENO DELANTERA", Categoria.nueva("PRUEBAS", 99), "AK150 RTX UNISHOCK");
        Variante pastillas = variantes.sembrar(
                Variante.nueva(p2, "152RTX2B", "CBI", Dinero.de(24_000), 4));

        Compra compra = registrar(
                ComandoRegistrarCompra.Linea.porTotal(filtro.getId(), 15, Dinero.de(200_000), null),
                ComandoRegistrarCompra.Linea.porUnitario(pastillas.getId(), 6,
                        new BigDecimal("14200"), null));

        // 200.000 + (6 x 14.200) = 285.200
        assertThat(compra.getTotal()).isEqualTo(Dinero.de(285_200));

        // LAS PARTES SUMAN EL TOTAL — la regla que el car-wash aprendio a golpes
        Dinero suma = compra.getLineas().stream()
                .map(l -> l.getCostoTotal())
                .reduce(Dinero.CERO, Dinero::mas);
        assertThat(suma).isEqualTo(compra.getTotal());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  INVENTARIO Y KARDEX
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("la compra sube el stock y fija el costo promedio")
    void subeStockYCosto() {
        registrar(ComandoRegistrarCompra.Linea.porTotal(filtro.getId(), 15, Dinero.de(200_000), null));

        assertThat(filtro.getStock()).isEqualTo(15);
        assertThat(filtro.getCostoPromedio()).isEqualByComparingTo("13333.3333");
    }

    @Test
    @DisplayName("dos compras a distinto precio dejan el promedio ponderado")
    void dosComprasPonderan() {
        registrar(ComandoRegistrarCompra.Linea.porUnitario(filtro.getId(), 10,
                new BigDecimal("1000"), null));
        registrar(ComandoRegistrarCompra.Linea.porUnitario(filtro.getId(), 10,
                new BigDecimal("2000"), null));

        assertThat(filtro.getStock()).isEqualTo(20);
        assertThat(filtro.getCostoPromedio()).isEqualByComparingTo("1500.0000");
    }

    @Test
    @DisplayName("el kardex guarda el saldo DESPUES del movimiento, no antes")
    void kardexGuardaElSaldoDespues() {
        registrar(ComandoRegistrarCompra.Linea.porUnitario(filtro.getId(), 10,
                new BigDecimal("1000"), null));
        registrar(ComandoRegistrarCompra.Linea.porUnitario(filtro.getId(), 10,
                new BigDecimal("2000"), null));

        List<MovimientoKardex> historial = kardex.historialDe(filtro.getId());
        assertThat(historial).hasSize(2);

        assertThat(historial.get(0).getTipo()).isEqualTo(TipoMovimiento.COMPRA);
        assertThat(historial.get(0).getCantidadDelta()).isEqualTo(10);
        assertThat(historial.get(0).getSaldoDespues()).isEqualTo(10);
        assertThat(historial.get(0).getCostoPromedioDespues()).isEqualByComparingTo("1000.0000");

        assertThat(historial.get(1).getSaldoDespues()).isEqualTo(20);
        assertThat(historial.get(1).getCostoPromedioDespues()).isEqualByComparingTo("1500.0000");
    }

    @Test
    @DisplayName("el movimiento de kardex apunta a la compra que lo origino")
    void kardexApuntaALaCompra() {
        Compra compra = registrar(
                ComandoRegistrarCompra.Linea.porTotal(filtro.getId(), 15, Dinero.de(200_000), null));

        assertThat(kardex.movimientos.get(0).getOrigenId()).isEqualTo(compra.getId());
    }

    @Test
    @DisplayName("carga la variante CON bloqueo — una vez por renglon")
    void cargaConBloqueo() {
        registrar(ComandoRegistrarCompra.Linea.porTotal(filtro.getId(), 15, Dinero.de(200_000), null));
        assertThat(variantes.vecesBuscadaParaModificar).isEqualTo(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PRECIO DE VENTA
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("precio vacio NO toca el precio del producto")
    void precioVacioNoTocaNada() {
        registrar(ComandoRegistrarCompra.Linea.porTotal(filtro.getId(), 15, Dinero.de(200_000), null));

        assertThat(filtro.getPrecio()).isEqualTo(Dinero.de(6_000));   // el de antes, intacto
    }

    @Test
    @DisplayName("precio escrito en la compra SI actualiza el precio de venta")
    void precioEscritoActualiza() {
        registrar(ComandoRegistrarCompra.Linea.porTotal(
                filtro.getId(), 15, Dinero.de(200_000), Dinero.de(19_000)));

        assertThat(filtro.getPrecio()).isEqualTo(Dinero.de(19_000));
        // utilidad = 19.000 - 13.333,3333
        assertThat(filtro.utilidadUnitaria()).isEqualByComparingTo("5666.6667");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  REGLAS QUE PROTEGEN
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("costo cero se rechaza con el motivo dicho")
    void rechazaCostoCero() {
        assertThatThrownBy(() -> registrar(
                ComandoRegistrarCompra.Linea.porTotal(filtro.getId(), 15, Dinero.CERO, null)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("100% de utilidad");
    }

    @Test
    @DisplayName("el mismo repuesto dos veces en una compra se rechaza")
    void rechazaVarianteRepetida() {
        assertThatThrownBy(() -> registrar(
                ComandoRegistrarCompra.Linea.porTotal(filtro.getId(), 5, Dinero.de(50_000), null),
                ComandoRegistrarCompra.Linea.porTotal(filtro.getId(), 3, Dinero.de(30_000), null)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("dos veces");
    }

    @Test
    @DisplayName("proveedor inexistente se rechaza")
    void rechazaProveedorInexistente() {
        assertThatThrownBy(() -> registrarCompra.ejecutar(new ComandoRegistrarCompra(
                UUID.randomUUID(), LocalDate.of(2026, 8, 13), "FV-1", FormaPago.EFECTIVO, null,
                ActoresDePrueba.administrador(),
                List.of(ComandoRegistrarCompra.Linea.porTotal(
                        filtro.getId(), 5, Dinero.de(50_000), null)))))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("proveedor no existe");
    }

    @Test
    @DisplayName("las dos fechas se conservan separadas: la de la factura y la del registro")
    void dosFechasSeparadas() {
        Compra compra = registrar(
                ComandoRegistrarCompra.Linea.porTotal(filtro.getId(), 15, Dinero.de(200_000), null));

        assertThat(compra.getFechaDocumento()).isEqualTo(LocalDate.of(2026, 8, 13));
        assertThat(compra.getFechaRegistro()).isEqualTo("2026-09-09T14:30:00Z");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FASE 4 — el repuesto nace durante la compra
    //
    //  Es la respuesta a que el cliente fije precios a mano: el repuesto existe
    //  cuando la mercancía llega, uno a la vez, en el momento en que importa.
    //  Nada de importar 8.824 referencias del catálogo del proveedor.
    // ─────────────────────────────────────────────────────────────────────────

    private Categoria frenos() {
        return categorias.activas().stream().filter(c -> c.getNombre().equals("FRENOS")).findFirst()
                .orElseGet(() -> categorias.sembrar(Categoria.nueva("FRENOS", 8)));
    }

    private ComandoCrearRepuesto pastillasNuevas(String codigo, Dinero precio) {
        return ComandoCrearRepuesto.conConceptoNuevo(
                "PASTILLAS FRENO DELANTERA", frenos().getId(), "AK150 RTX UNISHOCK",
                codigo, "CBI", precio, 4);
    }

    @Test
    @DisplayName("spec 0005, RF-009: un repuesto nuevo sin categoría no deja registrar la compra, y no crea el repuesto")
    void repuestoNuevoSinCategoria() {
        var sinCategoria = ComandoCrearRepuesto.conConceptoNuevo(
                "PASTILLAS FRENO DELANTERA", null, "AK150 RTX UNISHOCK", "152RTX2B", "CBI", Dinero.de(24_000), 4);

        assertThatThrownBy(() -> registrar(ComandoRegistrarCompra.Linea.porUnitarioCreando(
                sinCategoria, 6, new BigDecimal("14200"))))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessage(com.workshopmanagement.rdmotors.inventario.dominio.Producto.SIN_CATEGORIA);

        assertThat(variantes.buscarPorCodigo("152RTX2B")).isEmpty();
        assertThat(compras.historial(com.workshopmanagement.rdmotors.compras.dominio.FiltroCompras.sinFiltros(), 0, 10)
                .elementos()).isEmpty();
    }

    @Test
    @DisplayName("un renglón crea el repuesto que no existía y le deja su stock")
    void creaElRepuestoQueNoExistia() {
        Compra compra = registrar(ComandoRegistrarCompra.Linea.porUnitarioCreando(
                pastillasNuevas("152RTX2B", Dinero.de(24_000)), 6, new BigDecimal("14200")));

        Variante creada = variantes.buscarPorCodigo("152RTX2B").orElseThrow();

        assertThat(creada.getStock()).isEqualTo(6);
        assertThat(creada.getCostoPromedio()).isEqualByComparingTo("14200.0000");
        // El precio salió de su formulario de creación, no del renglón
        assertThat(creada.getPrecio()).isEqualTo(Dinero.de(24_000));
        assertThat(compra.getTotal()).isEqualTo(Dinero.de(85_200));   // 6 x 14.200
    }

    @Test
    @DisplayName("la factura mezcla repuestos que ya estaban con otros que nacen ahí")
    void mezclaExistentesYNuevos() {
        Compra compra = registrar(
                ComandoRegistrarCompra.Linea.porTotal(filtro.getId(), 15, Dinero.de(200_000), null),
                ComandoRegistrarCompra.Linea.porUnitarioCreando(
                        pastillasNuevas("152RTX2B", Dinero.de(24_000)), 6, new BigDecimal("14200")));

        assertThat(filtro.getStock()).isEqualTo(15);
        assertThat(variantes.buscarPorCodigo("152RTX2B").orElseThrow().getStock()).isEqualTo(6);
        assertThat(compra.getTotal()).isEqualTo(Dinero.de(285_200));

        // Las partes siguen sumando el total aunque un renglón haya creado su repuesto
        Dinero suma = compra.getLineas().stream()
                .map(l -> l.getCostoTotal())
                .reduce(Dinero.CERO, Dinero::mas);
        assertThat(suma).isEqualTo(compra.getTotal());
    }

    @Test
    @DisplayName("crear con modo TOTAL también funciona: 15 por $200.000")
    void creaConModoTotal() {
        registrar(ComandoRegistrarCompra.Linea.porTotalCreando(
                pastillasNuevas("152RTX2B", Dinero.de(24_000)), 15, Dinero.de(200_000)));

        Variante creada = variantes.buscarPorCodigo("152RTX2B").orElseThrow();
        assertThat(creada.getCostoPromedio()).isEqualByComparingTo("13333.3333");
    }

    @Test
    @DisplayName("un renglón nuevo REUTILIZA el concepto si se le pasa uno existente")
    void renglonNuevoPuedeReutilizarConcepto() {
        // El filtro INOKI ya existe; llega la marca FACTORY del MISMO filtro
        UUID conceptoFiltro = filtro.getProducto().getId();

        registrar(ComandoRegistrarCompra.Linea.porUnitarioCreando(
                ComandoCrearRepuesto.sobreConceptoExistente(
                        conceptoFiltro, "370P2NN", "FACTORY", Dinero.de(12_000), 5),
                6, new BigDecimal("6362")));

        Variante factory = variantes.buscarPorCodigo("370P2NN").orElseThrow();

        // Mismo concepto: la decisión §4 se sostiene también por este camino
        assertThat(factory.getProducto().getId()).isEqualTo(conceptoFiltro);
        assertThat(productos.buscarPorNombre("FILTRO ACEITE")).hasSize(1);
    }

    @Test
    @DisplayName("crear con un código que ya existe se rechaza diciendo cuál lo tiene")
    void codigoYaUsadoSeRechaza() {
        assertThatThrownBy(() -> registrar(ComandoRegistrarCompra.Linea.porUnitarioCreando(
                pastillasNuevas("352B59K", Dinero.de(24_000)), 6, new BigDecimal("14200"))))
                .isInstanceOf(CodigoDuplicadoException.class)
                .hasMessageContaining("FILTRO ACEITE");
    }

    @Test
    @DisplayName("dos renglones creando el MISMO código se rechazan antes de tocar nada")
    void dosRenglonesConElMismoCodigoNuevo() {
        assertThatThrownBy(() -> registrar(
                ComandoRegistrarCompra.Linea.porUnitarioCreando(
                        pastillasNuevas("152RTX2B", Dinero.de(24_000)), 6, new BigDecimal("14200")),
                ComandoRegistrarCompra.Linea.porUnitarioCreando(
                        pastillasNuevas("152rtx2b", Dinero.de(24_000)), 3, new BigDecimal("14200"))))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("dos veces");

        // Y no quedó nada a medias
        assertThat(variantes.buscarPorCodigo("152RTX2B")).isEmpty();
    }

    @Test
    @DisplayName("el precio de un repuesto nuevo NO puede venir también en el renglón")
    void precioEnDosSitiosSeRechaza() {
        // Dos fuentes para el mismo dato es la receta para que diverjan. El precio del repuesto
        // nuevo va donde el usuario lo escribió: en su formulario de creación.
        assertThatThrownBy(() -> new ComandoRegistrarCompra.Linea(
                null, pastillasNuevas("152RTX2B", Dinero.de(24_000)), 6,
                ModoCaptura.UNITARIO, null, new BigDecimal("14200"), Dinero.de(30_000)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("datos de creacion");
    }

    @Test
    @DisplayName("un renglón sin repuesto existente ni datos para crearlo se rechaza")
    void renglonSinRepuesto() {
        assertThatThrownBy(() -> new ComandoRegistrarCompra.Linea(
                null, null, 6, ModoCaptura.UNITARIO, null, new BigDecimal("14200"), null))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("no dice que repuesto es");
    }

    @Test
    @DisplayName("un renglón con repuesto existente Y datos de creación se rechaza")
    void renglonAmbiguo() {
        assertThatThrownBy(() -> new ComandoRegistrarCompra.Linea(
                filtro.getId(), pastillasNuevas("152RTX2B", Dinero.de(24_000)), 6,
                ModoCaptura.UNITARIO, null, new BigDecimal("14200"), null))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("uno u otro");
    }

    @Test
    @DisplayName("el repuesto creado queda con su movimiento de kardex apuntando a la compra")
    void elRepuestoNuevoTieneSuKardex() {
        Compra compra = registrar(ComandoRegistrarCompra.Linea.porUnitarioCreando(
                pastillasNuevas("152RTX2B", Dinero.de(24_000)), 6, new BigDecimal("14200")));

        Variante creada = variantes.buscarPorCodigo("152RTX2B").orElseThrow();
        var historial = kardex.historialDe(creada.getId());

        assertThat(historial).singleElement().satisfies(m -> {
            assertThat(m.getTipo()).isEqualTo(TipoMovimiento.COMPRA);
            assertThat(m.getCantidadDelta()).isEqualTo(6);
            assertThat(m.getSaldoDespues()).isEqualTo(6);
            assertThat(m.getOrigenId()).isEqualTo(compra.getId());
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SPEC 0002 · FASE 1 — con qué se pagó
    // ─────────────────────────────────────────────────────────────────────────

    private ComandoRegistrarCompra.Linea unFiltro() {
        return ComandoRegistrarCompra.Linea.porTotal(filtro.getId(), 5, Dinero.de(50_000), null);
    }

    private Compra pagada(FormaPago forma, UUID cuentaId) {
        return registrarCompra.ejecutar(new ComandoRegistrarCompra(
                jotapartes.getId(), LocalDate.of(2026, 8, 13), "FV-PAGO", forma, cuentaId,
                ActoresDePrueba.administrador(), List.of(unFiltro())));
    }

    @Test
    @DisplayName("una compra en efectivo queda en efectivo y sin cuenta")
    void compraEnEfectivo() {
        Compra compra = pagada(FormaPago.EFECTIVO, null);

        assertThat(compra.getFormaPago()).isEqualTo(FormaPago.EFECTIVO);
        assertThat(compra.getCuenta()).isNull();
    }

    @Test
    @DisplayName("una transferencia queda con la cuenta desde la que salió")
    void compraPorTransferencia() {
        CuentaPago nequi = cuentas.sembrar(CuentaPago.nueva("Nequi del dueño"));

        Compra compra = pagada(FormaPago.TRANSFERENCIA, nequi.getId());

        assertThat(compra.getFormaPago()).isEqualTo(FormaPago.TRANSFERENCIA);
        assertThat(compra.getCuenta()).isSameAs(nequi);
        assertThat(compras.buscar(compra.getId())).isPresent();
    }

    @Test
    @DisplayName("sin forma de pago no se registra: no hay efectivo por defecto")
    void sinFormaDePago() {
        assertThatThrownBy(() -> pagada(null, null))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("forma de pago");
        assertThat(kardex.movimientos).as("nada entró al inventario").isEmpty();
    }

    @Test
    @DisplayName("una transferencia sin cuenta se rechaza")
    void transferenciaSinCuenta() {
        assertThatThrownBy(() -> pagada(FormaPago.TRANSFERENCIA, null))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("cuenta");
        assertThat(filtro.getStock()).isZero();
    }

    @Test
    @DisplayName("una compra en efectivo con cuenta se rechaza: es un dato contradictorio")
    void efectivoConCuenta() {
        CuentaPago nequi = cuentas.sembrar(CuentaPago.nueva("Nequi del dueño"));

        assertThatThrownBy(() -> pagada(FormaPago.EFECTIVO, nequi.getId()))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("efectivo no lleva cuenta");
    }

    @Test
    @DisplayName("una cuenta desactivada no se puede usar en una compra nueva")
    void cuentaDesactivada() {
        CuentaPago vieja = cuentas.sembrar(CuentaPago.nueva("Bancolombia ···4521"));
        vieja.desactivar();

        assertThatThrownBy(() -> pagada(FormaPago.TRANSFERENCIA, vieja.getId()))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("desactivada");
        assertThat(filtro.getStock()).isZero();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LA LLAVE CONTRA EL DOBLE REGISTRO (spec 0009, RF-009)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("la misma compra dos veces (misma llave) entra una sola vez: el stock sube una vez y el costo se recalcula una vez")
    void llaveRepetida() {
        UUID llave = UUID.randomUUID();

        Compra primera = conLlave(llave, ComandoRegistrarCompra.Linea.porUnitario(
                filtro.getId(), 10, new BigDecimal("10000"), null));
        Compra segunda = conLlave(llave, ComandoRegistrarCompra.Linea.porUnitario(
                filtro.getId(), 10, new BigDecimal("10000"), null));

        assertThat(segunda.getId()).isEqualTo(primera.getId());
        assertThat(filtro.getStock()).isEqualTo(10);
        assertThat(filtro.getCostoPromedio()).isEqualByComparingTo("10000.0000");
        assertThat(kardex.movimientos).hasSize(1);
    }

    @Test
    @DisplayName("la compra repetida se devuelve ANTES de bloquear nada: no vuelve a pedir el repuesto con intención de moverlo")
    void laRepetidaNoBloqueaNada() {
        UUID llave = UUID.randomUUID();
        conLlave(llave, ComandoRegistrarCompra.Linea.porUnitario(filtro.getId(), 10, new BigDecimal("10000"), null));
        int bloqueosDelPrimero = variantes.vecesBuscadaParaModificar;

        conLlave(llave, ComandoRegistrarCompra.Linea.porUnitario(filtro.getId(), 10, new BigDecimal("10000"), null));

        // Mirar la llave primero es lo que hace que un reintento no se quede esperando un repuesto ocupado,
        // ni pida el turno de caja de una compra que ya entró.
        assertThat(variantes.vecesBuscadaParaModificar).isEqualTo(bloqueosDelPrimero);
    }

    @Test
    @DisplayName("dos compras distintas llevan llaves distintas y las dos entran")
    void llavesDistintas() {
        conLlave(UUID.randomUUID(), ComandoRegistrarCompra.Linea.porUnitario(
                filtro.getId(), 10, new BigDecimal("10000"), null));
        conLlave(UUID.randomUUID(), ComandoRegistrarCompra.Linea.porUnitario(
                filtro.getId(), 10, new BigDecimal("10000"), null));

        assertThat(filtro.getStock()).isEqualTo(20);
        assertThat(kardex.movimientos).hasSize(2);
    }

    @Test
    @DisplayName("una compra sin llave no se registra: reintentarla sumaría el stock dos veces")
    void sinLlaveNoSeRegistra() {
        assertThatThrownBy(() -> conLlave(null, ComandoRegistrarCompra.Linea.porUnitario(
                filtro.getId(), 10, new BigDecimal("10000"), null)))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("llave");
        assertThat(filtro.getStock()).isZero();
    }

    private Compra conLlave(UUID llave, ComandoRegistrarCompra.Linea... lineas) {
        return registrarCompra.ejecutar(new ComandoRegistrarCompra(
                jotapartes.getId(), LocalDate.of(2026, 8, 13), "FV-9912",
                FormaPago.EFECTIVO, null, ActoresDePrueba.administrador(), List.of(lineas), false, llave));
    }

    @Test
    @DisplayName("una cuenta que no existe se rechaza")
    void cuentaInexistente() {
        assertThatThrownBy(() -> pagada(FormaPago.TRANSFERENCIA, UUID.randomUUID()))
                .isInstanceOf(ReglaDeNegocioException.class)
                .hasMessageContaining("cuenta no existe");
    }
}
