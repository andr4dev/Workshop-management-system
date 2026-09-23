package com.workshopmanagement.rdmotors.compras.aplicacion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compras.dominio.LineaCompra;
import com.workshopmanagement.rdmotors.compras.dominio.ModoCaptura;
import com.workshopmanagement.rdmotors.compras.dominio.Proveedor;
import com.workshopmanagement.rdmotors.inventario.aplicacion.CrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.MovimientoKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/**
 * Una tienda en memoria para probar corregir y anular: los dobles, los casos de uso ya cableados, y
 * atajos para comprar, vender y armar correcciones sin repetir veinte líneas en cada prueba.
 */
final class EscenarioCompras {

    final Falsos.VariantesEnMemoria variantes = new Falsos.VariantesEnMemoria();
    final Falsos.ProductosEnMemoria productos = new Falsos.ProductosEnMemoria();
    final Falsos.CategoriasEnMemoria categorias = new Falsos.CategoriasEnMemoria();
    final Falsos.ProveedoresEnMemoria proveedores = new Falsos.ProveedoresEnMemoria();
    final Falsos.CuentasEnMemoria cuentas = new Falsos.CuentasEnMemoria();
    final Falsos.ComprasEnMemoria compras = new Falsos.ComprasEnMemoria();
    final Falsos.KardexEnMemoria kardex = new Falsos.KardexEnMemoria();
    final Falsos.AuditoriaEnMemoria auditoria = new Falsos.AuditoriaEnMemoria();
    final Falsos.TurnosEnMemoria turnos = new Falsos.TurnosEnMemoria();
    final Falsos.UsuariosEnMemoria usuarios = new Falsos.UsuariosEnMemoria();
    final Falsos.RelojFijo reloj = new Falsos.RelojFijo("2026-09-13T15:00:00Z");

    final CrearRepuesto crearRepuesto = new CrearRepuesto(variantes, productos, categorias);
    final RegistrarCompra registrar = new RegistrarCompra(compras, proveedores, cuentas, turnos, variantes,
            kardex, crearRepuesto, reloj);
    final CorregirCompra corregir = new CorregirCompra(compras, proveedores, cuentas, turnos, variantes,
            kardex, crearRepuesto, auditoria, reloj);
    final AnularCompra anular = new AnularCompra(compras, turnos, variantes, kardex, auditoria, reloj);
    final InventarioDeCompra inventario = new InventarioDeCompra(variantes, kardex, compras, crearRepuesto);

    final Proveedor jotapartes = proveedores.sembrar(Proveedor.nuevo("Importadora Jotapartes", null, null));
    final Proveedor otroProveedor = proveedores.sembrar(Proveedor.nuevo("Importadora JC", null, null));
    final CuentaPago nequi = cuentas.sembrar(CuentaPago.nueva("Nequi del dueño"));
    final Actor usuario = usuarios.sembrar(ActoresDePrueba.administrador());

    /** Un repuesto nuevo, sin stock, con su concepto sembrado en los dos repositorios. */
    Variante repuesto(String codigo, long precio) {
        Producto producto = productos.sembrar(Producto.nuevo("REPUESTO " + codigo, Categoria.nueva("PRUEBAS", 99), null));
        return variantes.sembrar(Variante.nueva(producto, codigo, "INOKI", Dinero.de(precio), 5));
    }

    Compra comprar(ComandoRegistrarCompra.Linea... lineas) {
        return registrar.ejecutar(new ComandoRegistrarCompra(jotapartes.getId(),
                LocalDate.of(2026, 9, 1), "FV-" + UUID.randomUUID().toString().substring(0, 6),
                FormaPago.EFECTIVO, null, usuario, List.of(lineas)));
    }

    static ComandoRegistrarCompra.Linea unidades(Variante v, int cantidad, long costoUnitario) {
        return ComandoRegistrarCompra.Linea.porUnitario(v.getId(), cantidad,
                BigDecimal.valueOf(costoUnitario), null);
    }

    static ComandoRegistrarCompra.Linea unidadesConPrecio(Variante v, int cantidad, long costoUnitario,
                                                          long precio) {
        return ComandoRegistrarCompra.Linea.porUnitario(v.getId(), cantidad,
                BigDecimal.valueOf(costoUnitario), Dinero.de(precio));
    }

    /** Una venta hecha a mano: descuenta y deja su salida en el kardex, como la hará el mostrador. */
    MovimientoKardex vender(Variante v, int cantidad) {
        v.descontar(cantidad);
        MovimientoKardex salida = MovimientoKardex.porVenta(v, cantidad, UUID.randomUUID(), usuario.id(),
                Instant.parse("2026-09-13T16:00:00Z"));
        kardex.agregar(salida);
        return salida;
    }

    /** La anulación de esa venta, como la hace {@code AnularVenta}: el stock vuelve y la reversión apunta a la salida. */
    void anularVenta(Variante v, MovimientoKardex salida) {
        v.reponerPorReversion(-salida.getCantidadDelta());
        kardex.agregar(MovimientoKardex.porReversionDeVenta(v, salida, salida.getOrigenId(), "anulada",
                usuario.id(), Instant.parse("2026-09-13T17:00:00Z")));
    }

    LineaCompra renglonDe(Compra compra, Variante v) {
        return compra.lineasVigentes().stream()
                .filter(l -> l.getVariante().getId().equals(v.getId()))
                .findFirst()
                .orElseThrow();
    }

    /** El renglón tal como está: lo que manda la pantalla si nadie lo tocó. */
    static ComandoCorregirCompra.Linea igual(LineaCompra linea) {
        UUID varianteId = linea.getVariante().getId();
        var datos = linea.getModoCaptura() == ModoCaptura.TOTAL
                ? ComandoRegistrarCompra.Linea.porTotal(varianteId, linea.getCantidad(),
                        linea.getCostoTotal(), linea.getPrecioVenta())
                : ComandoRegistrarCompra.Linea.porUnitario(varianteId, linea.getCantidad(),
                        linea.getCostoUnitario(), linea.getPrecioVenta());
        return new ComandoCorregirCompra.Linea(linea.getId(), datos);
    }

    /** El renglón con otros datos, en el mismo lugar de la factura. */
    static ComandoCorregirCompra.Linea en(LineaCompra linea, ComandoRegistrarCompra.Linea datos) {
        return new ComandoCorregirCompra.Linea(linea.getId(), datos);
    }

    static ComandoCorregirCompra.Linea nuevo(ComandoRegistrarCompra.Linea datos) {
        return new ComandoCorregirCompra.Linea(null, datos);
    }

    /** Misma cabecera, estos renglones. {@code null} = no tocar renglones. */
    ComandoCorregirCompra correccion(Compra compra, List<ComandoCorregirCompra.Linea> lineas) {
        return new ComandoCorregirCompra(compra.getId(), version(compra), "error al digitar",
                usuario, compra.getProveedor().getId(), compra.getFechaDocumento(),
                compra.getNumeroFactura(), compra.getFormaPago(),
                compra.getCuenta() == null ? null : compra.getCuenta().getId(), lineas);
    }

    static long version(Compra compra) {
        return compra.getVersion() == null ? 0L : compra.getVersion();
    }

    int movimientosDe(Variante v) {
        return kardex.historialDe(v.getId()).size();
    }
}
