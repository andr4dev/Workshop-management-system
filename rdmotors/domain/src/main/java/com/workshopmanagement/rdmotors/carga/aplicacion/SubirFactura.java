package com.workshopmanagement.rdmotors.carga.aplicacion;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.carga.dominio.CargaDeInventario;
import com.workshopmanagement.rdmotors.carga.dominio.EstadoCarga;
import com.workshopmanagement.rdmotors.carga.dominio.FacturaLeida;
import com.workshopmanagement.rdmotors.carga.dominio.FacturaNoReconocidaException;
import com.workshopmanagement.rdmotors.carga.dominio.FacturaYaCargadaException;
import com.workshopmanagement.rdmotors.carga.dominio.Nit;
import com.workshopmanagement.rdmotors.carga.dominio.ProponedorDeMarca;
import com.workshopmanagement.rdmotors.carga.dominio.puerto.LectorDeFactura;
import com.workshopmanagement.rdmotors.carga.dominio.puerto.RepositorioCargas;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compras.dominio.EstadoCompra;
import com.workshopmanagement.rdmotors.compras.dominio.Proveedor;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCompras;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCuentas;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioProveedores;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioCategorias;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;

/**
 * CASO DE USO — subir una factura y armar su pre-carga (spec 0012, H1).
 *
 * <p>Lee el archivo con el lector que lo reconozca, busca al proveedor por el NIT impreso, propone marca y categoría,
 * y guarda el borrador. <b>El inventario no se toca</b>: eso es confirmar. El archivo tampoco se guarda: se lee y se
 * descarta, y lo que queda es lo leído.
 */
@Transactional
public class SubirFactura {

    /** Una factura de 32 páginas pesa 700 KB. Cinco megas es un archivo equivocado, no una factura grande. */
    public static final int TAMANO_MAXIMO = 5 * 1024 * 1024;

    private final List<LectorDeFactura> lectores;
    private final RepositorioCargas cargas;
    private final RepositorioProveedores proveedores;
    private final RepositorioCompras compras;
    private final RepositorioVariantes variantes;
    private final ArmadoDelDetalle detalle;
    private final Reloj reloj;

    /** @param lectores en el orden en que se prueban: el primero que reconozca el archivo es el que lo lee */
    public SubirFactura(List<LectorDeFactura> lectores, RepositorioCargas cargas, RepositorioProveedores proveedores,
                        RepositorioCuentas cuentas, RepositorioCompras compras, RepositorioVariantes variantes,
                        RepositorioCategorias categorias, Reloj reloj) {
        this.lectores = List.copyOf(lectores);
        this.cargas = cargas;
        this.proveedores = proveedores;
        this.compras = compras;
        this.variantes = variantes;
        this.detalle = new ArmadoDelDetalle(variantes, categorias, proveedores, cuentas);
        this.reloj = reloj;
    }

    public DetalleCarga ejecutar(String nombreArchivo, byte[] contenido, Actor actor) {
        actor.exigirAdministrador();
        if (contenido == null || contenido.length == 0) {
            throw new FacturaNoReconocidaException("El archivo está vacío");
        }
        if (contenido.length > TAMANO_MAXIMO) {
            throw new FacturaNoReconocidaException("El archivo pesa más de 5 MB: una factura no pesa tanto. Revisa "
                    + "que sea el archivo correcto.");
        }
        LectorDeFactura lector = lectores.stream()
                .filter(l -> l.reconoce(nombreArchivo, contenido))
                .findFirst()
                .orElseThrow(() -> new FacturaNoReconocidaException(porQueNoSeLee(nombreArchivo)));
        FacturaLeida factura = lector.leer(contenido);

        exigirQueNoEsteCargada(factura);
        UUID proveedorId = factura.nitProveedor() == null ? null
                : proveedores.activos().stream()
                        .filter(p -> Nit.coincide(p.getNit(), factura.nitProveedor()))
                        .map(Proveedor::getId)
                        .findFirst()
                        .orElse(null);

        CargaDeInventario carga = CargaDeInventario.desde(factura, nombreArchivo, proveedorId,
                new ProponedorDeMarca(variantes.marcasEnUso()), detalle.categoriasConocidas(), actor.id(),
                reloj.ahora());
        return detalle.armar(cargas.guardar(carga));
    }

    /**
     * La misma factura dos veces entraría la mercancía dos veces. Una confirmada cuya compra se anuló sí se puede
     * volver a subir: anular es justo la salida para cargarla de nuevo bien.
     */
    private void exigirQueNoEsteCargada(FacturaLeida factura) {
        if (factura.nitProveedor() == null || factura.numeroFactura() == null) {
            return;
        }
        Optional<CargaDeInventario> previa = cargas.deLaFactura(factura.nitProveedor(), factura.numeroFactura());
        if (previa.isEmpty()) {
            return;
        }
        CargaDeInventario ya = previa.get();
        if (ya.getEstado() == EstadoCarga.BORRADOR) {
            throw new FacturaYaCargadaException("Ya hay una pre-carga de la factura " + factura.numeroFactura()
                    + " sin terminar. Ábrela en Cargas, o descártala para subirla de nuevo.", ya.getId());
        }
        boolean suCompraSigue = ya.getCompraId() != null && compras.buscar(ya.getCompraId())
                .map(c -> c.getEstado() == EstadoCompra.VIGENTE).orElse(false);
        if (suCompraSigue) {
            throw new FacturaYaCargadaException("La factura " + factura.numeroFactura() + " ya se cargó y está en "
                    + "Compras. Si algo quedó mal, se corrige allá, o se anula esa compra y se sube de nuevo.",
                    ya.getId());
        }
    }

    private static String porQueNoSeLee(String nombreArchivo) {
        String nombre = nombreArchivo == null ? "" : nombreArchivo.toLowerCase(Locale.ROOT);
        if (nombre.endsWith(".xls")) {
            return "Ese es un Excel de los viejos (.xls). Ábrelo en Excel y guárdalo como .xlsx, o como .csv, y "
                    + "súbelo otra vez.";
        }
        return "Ese archivo no se puede leer. Sube la factura en PDF, un Excel (.xlsx) o un .csv.";
    }
}
