package com.workshopmanagement.rdmotors.carga.aplicacion;

import java.util.ArrayList;
import java.util.List;

import com.workshopmanagement.rdmotors.carga.dominio.FacturaLeida;
import com.workshopmanagement.rdmotors.carga.dominio.FacturasDePrueba;
import com.workshopmanagement.rdmotors.carga.dominio.puerto.LectorDeFactura;
import com.workshopmanagement.rdmotors.compartido.ActoresDePrueba;
import com.workshopmanagement.rdmotors.compartido.Falsos;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarCompra;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;
import com.workshopmanagement.rdmotors.compras.dominio.Proveedor;
import com.workshopmanagement.rdmotors.inventario.aplicacion.CrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;
import com.workshopmanagement.rdmotors.inventario.dominio.Producto;
import com.workshopmanagement.rdmotors.inventario.dominio.Variante;

/** Una tienda en memoria con los casos de uso de la carga ya cableados. */
final class EscenarioCargas {

    final Falsos.CargasEnMemoria cargas = new Falsos.CargasEnMemoria();
    final Falsos.VariantesEnMemoria variantes = new Falsos.VariantesEnMemoria();
    final Falsos.CategoriasEnMemoria categorias = new Falsos.CategoriasEnMemoria();
    final Falsos.ProveedoresEnMemoria proveedores = new Falsos.ProveedoresEnMemoria();
    final Falsos.CuentasEnMemoria cuentas = new Falsos.CuentasEnMemoria();
    final Falsos.ComprasEnMemoria compras = new Falsos.ComprasEnMemoria();
    final Falsos.KardexEnMemoria kardex = new Falsos.KardexEnMemoria();
    final Falsos.ProductosEnMemoria productos = new Falsos.ProductosEnMemoria();
    final Falsos.TurnosEnMemoria turnos = new Falsos.TurnosEnMemoria();
    final Falsos.RelojFijo reloj = new Falsos.RelojFijo("2026-09-25T15:00:00Z");

    /** Lo que "lee" el archivo: cada prueba pone la factura que quiere. */
    final List<LectorDeFactura> lectores = new ArrayList<>();

    final Actor dueno = ActoresDePrueba.administrador();
    final Actor cajero = ActoresDePrueba.cajero();

    /** Guardado como lo escribe cualquiera en la ficha: con puntos y dígito de verificación. */
    final Proveedor jotapartes = proveedores.sembrar(Proveedor.nuevo("Importadora Jotapartes", "900.576.528-1", null));
    final Proveedor otro = proveedores.sembrar(Proveedor.nuevo("Importadora JC", "800123456", null));
    final CuentaPago nequi = cuentas.sembrar(CuentaPago.nueva("Nequi del dueño"));

    final Categoria electrico = categorias.sembrar(Categoria.nueva("ELÉCTRICO", 4));
    final Categoria empaques = categorias.sembrar(Categoria.nueva("EMPAQUES Y SELLOS", 2));
    final Categoria motor = categorias.sembrar(Categoria.nueva("MOTOR", 1));

    /** Con los lectores que la prueba haya puesto hasta ahora: el caso de uso los copia al nacer, como en Spring. */
    SubirFactura subir() {
        return new SubirFactura(lectores, cargas, proveedores, cuentas, compras, variantes, categorias, reloj);
    }

    final ConsultarCarga consultar = new ConsultarCarga(cargas, variantes, categorias, proveedores, cuentas);
    final EditarCarga editar = new EditarCarga(cargas, variantes, categorias, proveedores, cuentas, reloj);
    final DescartarCarga descartar = new DescartarCarga(cargas, reloj);
    final RegistrarCompra registrarCompra = new RegistrarCompra(compras, proveedores, cuentas, turnos, variantes,
            kardex, new CrearRepuesto(variantes, productos, categorias), reloj);
    final ConfirmarCarga confirmar = new ConfirmarCarga(cargas, compras, registrarCompra, variantes, categorias,
            proveedores, cuentas, reloj);

    /** El PDF "se lee" como esta factura. */
    EscenarioCargas leyendo(FacturaLeida factura) {
        lectores.clear();
        lectores.add(new Falsos.LectorFalso(".pdf", factura));
        return this;
    }

    DetalleCarga subirPdf(FacturaLeida factura) {
        leyendo(factura);
        return subir().ejecutar("fv09005765280152600000477.pdf", new byte[] {'%', 'P', 'D', 'F'}, dueno);
    }

    DetalleCarga subirLaMag() {
        return subirPdf(FacturasDePrueba.jotapartes(FacturasDePrueba.bujia(), FacturasDePrueba.kitEmpaques(),
                FacturasDePrueba.tensor()));
    }

    /** La MAG477 de tres renglones, lista para confirmar: proveedor, fecha, efectivo, y la marca que le faltaba al kit. */
    DetalleCarga laMagLista() {
        java.util.UUID id = subirLaMag().carga().getId();
        editar.cambiarDatos(id, jotapartes.getId(), "MAG477", java.time.LocalDate.of(2026, 8, 31),
                com.workshopmanagement.rdmotors.compartido.dominio.FormaPago.EFECTIVO, null, dueno);
        return editar.cambiarMarca(id, java.util.List.of(), true, "NACIONAL", dueno);
    }

    /** Un repuesto que ya está en el inventario, con stock. */
    Variante yaExiste(String codigo, long precio, int stock) {
        Producto producto = Producto.nuevo("REPUESTO " + codigo, empaques, null);
        Variante v = Variante.nueva(producto, codigo, "INOKI", Dinero.de(precio), 2);
        if (stock > 0) {
            v.reponerPorCompra(stock, java.math.BigDecimal.valueOf(precio / 2));
        }
        return variantes.sembrar(v);
    }
}
