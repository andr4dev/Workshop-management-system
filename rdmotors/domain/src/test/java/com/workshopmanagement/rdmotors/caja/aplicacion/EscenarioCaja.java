package com.workshopmanagement.rdmotors.caja.aplicacion;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.workshopmanagement.rdmotors.caja.dominio.CategoriaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.Gasto;
import com.workshopmanagement.rdmotors.caja.dominio.NaturalezaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.Retiro;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.clientes.aplicacion.AnularAbono;
import com.workshopmanagement.rdmotors.clientes.aplicacion.FiarVenta;
import com.workshopmanagement.rdmotors.clientes.aplicacion.RegistrarAbono;
import com.workshopmanagement.rdmotors.clientes.dominio.Abono;
import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.DatosCliente;
import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.correo.aplicacion.EncolarCorreoDelCierre;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compras.aplicacion.AnularCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.ComandoRegistrarCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarCompra;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;
import com.workshopmanagement.rdmotors.compras.dominio.Proveedor;
import com.workshopmanagement.rdmotors.inventario.aplicacion.CrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;
import com.workshopmanagement.rdmotors.ventas.aplicacion.AnularVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.CobrarVenta;
import com.workshopmanagement.rdmotors.ventas.aplicacion.ComandoCobrarVenta;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;

/**
 * Una tienda en memoria para probar la caja (spec 0006): turnos, ventas, gastos, retiros y compras de caja con
 * sus casos de uso de verdad, y atajos para armar el día del ejemplo del spec sin repetir veinte líneas.
 *
 * <p>El reloj avanza un minuto con cada acción: así los turnos cerrados tienen un orden y un gasto se registra
 * después de abrir el turno, como en la tienda.
 */
final class EscenarioCaja {

    /** Un reloj que avanza solo: cada vez que se pregunta la hora, pasó un minuto. */
    static final class RelojQueAvanza implements Reloj {
        private Instant ahora = Instant.parse("2026-09-16T13:00:00Z");

        @Override
        public Instant ahora() {
            ahora = ahora.plus(Duration.ofMinutes(1));
            return ahora;
        }

        @Override
        public LocalDate hoy() {
            return LocalDate.ofInstant(ahora, ZoneId.of("America/Bogota"));
        }
    }

    final RelojQueAvanza reloj = new RelojQueAvanza();
    final Falsos.TurnosEnMemoria turnos = new Falsos.TurnosEnMemoria();
    final Falsos.UsuariosEnMemoria usuarios = new Falsos.UsuariosEnMemoria();
    final Falsos.VentasEnMemoria ventas = new Falsos.VentasEnMemoria();
    final Falsos.VariantesEnMemoria variantes = new Falsos.VariantesEnMemoria();
    final Falsos.KardexEnMemoria kardex = new Falsos.KardexEnMemoria();
    final Falsos.AuditoriaEnMemoria auditoria = new Falsos.AuditoriaEnMemoria();
    final Falsos.GastosEnMemoria gastos = new Falsos.GastosEnMemoria();
    final Falsos.RetirosEnMemoria retiros = new Falsos.RetirosEnMemoria();
    final Falsos.CategoriasGastoEnMemoria categorias = new Falsos.CategoriasGastoEnMemoria();
    final Falsos.CuentasEnMemoria cuentas = new Falsos.CuentasEnMemoria();
    final Falsos.ComprasEnMemoria compras = new Falsos.ComprasEnMemoria();
    final Falsos.ProveedoresEnMemoria proveedores = new Falsos.ProveedoresEnMemoria();
    final Falsos.ProductosEnMemoria productos = new Falsos.ProductosEnMemoria();
    final Falsos.CategoriasEnMemoria categoriasRepuesto = new Falsos.CategoriasEnMemoria();

    final Falsos.ClientesEnMemoria clientes = new Falsos.ClientesEnMemoria();
    final Falsos.DeudasEnMemoria deudas = new Falsos.DeudasEnMemoria();
    final Falsos.AbonosEnMemoria abonos = new Falsos.AbonosEnMemoria();
    final FiarVenta fiar = new FiarVenta(clientes, deudas, abonos);

    final CalcularArqueo calcularArqueo = new CalcularArqueo(ventas, gastos, retiros, compras, abonos);
    final AbrirTurno abrirTurno = new AbrirTurno(turnos, reloj);
    final CobrarVenta cobrarVenta = new CobrarVenta(ventas, turnos, variantes, kardex, auditoria, fiar, reloj);
    final AnularVenta anularVenta = new AnularVenta(ventas, turnos, variantes, kardex, auditoria, fiar, reloj);
    final RegistrarGasto registrarGasto = new RegistrarGasto(gastos, categorias, cuentas, turnos, calcularArqueo, reloj);
    final AnularGasto anularGasto = new AnularGasto(gastos, turnos, auditoria, reloj);
    final RegistrarRetiro registrarRetiro = new RegistrarRetiro(retiros, turnos, calcularArqueo, reloj);
    final AnularRetiro anularRetiro = new AnularRetiro(retiros, turnos, auditoria, reloj);
    final RegistrarCompra registrarCompra = new RegistrarCompra(compras, proveedores, cuentas, turnos, variantes,
            kardex, new CrearRepuesto(variantes, productos, categoriasRepuesto), reloj);
    final AnularCompra anularCompra = new AnularCompra(compras, turnos, variantes, kardex, auditoria, reloj);
    /** El correo del cierre (spec 0010): sin destinatarios no encola nada, como en una tienda sin configurar. */
    final Falsos.AjustesDeCorreoEnMemoria ajustesDeCorreo = new Falsos.AjustesDeCorreoEnMemoria();
    final Falsos.CorreosEnMemoria correos = new Falsos.CorreosEnMemoria();
    final CerrarTurno cerrarTurno = new CerrarTurno(turnos, calcularArqueo, auditoria,
            new EncolarCorreoDelCierre(ajustesDeCorreo, correos), reloj);
    final EscribirObservaciones escribirObservaciones = new EscribirObservaciones(turnos);
    final ConsultarTurnos consultarTurnos = new ConsultarTurnos(turnos, gastos, retiros, compras, ventas,
            calcularArqueo, usuarios, abonos, clientes);
    final RegistrarAbono registrarAbono = new RegistrarAbono(abonos, clientes, deudas, turnos, reloj);
    final AnularAbono anularAbono = new AnularAbono(abonos, clientes, deudas, turnos, auditoria, reloj);

    final Actor cajero = usuarios.sembrar(ActoresDePrueba.cajero());
    /** El gasto por fuera del cajón y las compras son suyos (spec 0004, §5); puede operar en el turno del cajero. */
    final Actor administrador = usuarios.sembrar(ActoresDePrueba.administrador());
    final CategoriaGasto fletes = categorias.sembrar(CategoriaGasto.nueva("Transporte y fletes", NaturalezaGasto.GASTO));
    final CategoriaGasto arriendo = categorias.sembrar(CategoriaGasto.nueva("Arriendo", NaturalezaGasto.GASTO));
    final CuentaPago nequi = cuentas.sembrar(CuentaPago.nueva("Nequi del dueño"));
    final Proveedor jotapartes = proveedores.sembrar(Proveedor.nuevo("Importadora Jotapartes", null, null));
    private int siguienteCodigo = 1;

    TurnoCaja abrir(long fondo) {
        return abrirTurno.ejecutar(Dinero.de(fondo), cajero);
    }

    TurnoCaja turnoAbierto() {
        return turnos.abierto().orElseThrow();
    }

    /** Un repuesto de ese precio con stock de sobra. */
    Variante repuesto(long precio) {
        Producto producto = productos.sembrar(Producto.nuevo("REPUESTO " + siguienteCodigo,
                Categoria.nueva("PRUEBAS", 99), null));
        Variante variante = variantes.sembrar(Variante.nueva(producto, "COD-" + siguienteCodigo++, "INOKI",
                Dinero.de(precio), 1));
        variante.reponerPorCompra(100, new BigDecimal("1000"));
        return variante;
    }

    Venta vender(long efectivo, long transferencia) {
        Variante variante = repuesto(efectivo + transferencia);
        List<ComandoCobrarVenta.Pago> pagos = new java.util.ArrayList<>();
        if (efectivo > 0) pagos.add(new ComandoCobrarVenta.Pago(FormaPago.EFECTIVO, efectivo, null));
        if (transferencia > 0) pagos.add(new ComandoCobrarVenta.Pago(FormaPago.TRANSFERENCIA, transferencia, null));
        return cobrarVenta.ejecutar(new ComandoCobrarVenta(UUID.randomUUID(),
                List.of(new ComandoCobrarVenta.Renglon(variante.getId(), 1, efectivo + transferencia)), null, pagos,
                cajero)).venta();
    }

    Venta venderEnEfectivo(long monto) {
        return vender(monto, 0);
    }

    /** Un cliente con los datos para fiarle (spec 0008). */
    Cliente cliente(String nombre, String cedula) {
        return clientes.sembrar(Cliente.nuevo(new DatosCliente(nombre, cedula, "300" + cedula, null, null),
                cajero.id(), reloj.ahora()));
    }

    /** Un abono de ese cliente, en efectivo, a lo más viejo primero (spec 0008). */
    Abono abonar(Cliente cliente, long monto) {
        return registrarAbono.ejecutar(UUID.randomUUID(), cliente.getId(), Dinero.de(monto), FormaPago.EFECTIVO, null,
                null, null, cajero);
    }

    /** Un abono por transferencia: no entra al cajón. */
    Abono abonarPorTransferencia(Cliente cliente, long monto) {
        return registrarAbono.ejecutar(UUID.randomUUID(), cliente.getId(), Dinero.de(monto), FormaPago.TRANSFERENCIA,
                "REF-1", null, null, cajero);
    }

    /** Una venta de {@code total} fiada a ese cliente; paga en efectivo {@code pagaEfectivo} y el resto lo debe. */
    Venta fiar(Cliente cliente, long total, long pagaEfectivo) {
        Variante variante = repuesto(total);
        List<ComandoCobrarVenta.Pago> pagos = pagaEfectivo > 0
                ? List.of(new ComandoCobrarVenta.Pago(FormaPago.EFECTIVO, pagaEfectivo, null)) : List.of();
        return cobrarVenta.ejecutar(new ComandoCobrarVenta(UUID.randomUUID(),
                List.of(new ComandoCobrarVenta.Renglon(variante.getId(), 1, total)), null, pagos, cliente.getId(),
                total - pagaEfectivo, cajero)).venta();
    }

    Venta anular(Venta venta) {
        return anularVenta.ejecutar(venta.getId(), "Cliente se arrepintió", cajero);
    }

    Gasto gastoDelCajon(long monto) {
        return registrarGasto.ejecutar(ComandoRegistrarGasto.delCajon(UUID.randomUUID(), fletes.getId(),
                Dinero.de(monto), "Flete Jotapartes FV-9912", cajero));
    }

    Gasto arriendoPorNequi(long monto) {
        return registrarGasto.ejecutar(ComandoRegistrarGasto.porFuera(UUID.randomUUID(), arriendo.getId(),
                Dinero.de(monto), "Arriendo de septiembre", FormaPago.TRANSFERENCIA, nequi.getId(), reloj.hoy(),
                administrador));
    }

    Retiro retiro(long monto) {
        return registrarRetiro.ejecutar(UUID.randomUUID(), Dinero.de(monto), "Se lo llevó don Rubén", false, cajero);
    }

    Compra compra(long total, boolean deCaja) {
        Variante variante = repuesto(total * 2);
        return registrarCompra.ejecutar(new ComandoRegistrarCompra(jotapartes.getId(), reloj.hoy(), null,
                FormaPago.EFECTIVO, null, administrador,
                List.of(ComandoRegistrarCompra.Linea.porTotal(variante.getId(), 1, Dinero.de(total), null)), deCaja));
    }

    TurnoCaja cerrar(long contado) {
        return cerrarTurno.ejecutar(turnoAbierto().getId(), Dinero.de(contado), cajero);
    }

    /**
     * El día del ejemplo del spec (§2): abre con $100.000; se venden $223.400 en efectivo y $111.000 por
     * transferencia; se anula una venta de AYER pagada en efectivo ($70.000); flete de $15.000 del cajón; el
     * dueño se lleva $100.000; y se paga con plata del cajón una compra de $50.000. Debería haber $88.400.
     *
     * <p>Deja abierto el turno de hoy.
     */
    TurnoCaja diaDelEjemplo() {
        abrir(100_000);
        Venta deAyer = venderEnEfectivo(70_000);
        cerrar(170_000);

        abrir(100_000);
        venderEnEfectivo(223_400);
        vender(0, 111_000);
        anular(deAyer);
        gastoDelCajon(15_000);
        retiro(100_000);
        compra(50_000, true);
        return turnoAbierto();
    }
}
