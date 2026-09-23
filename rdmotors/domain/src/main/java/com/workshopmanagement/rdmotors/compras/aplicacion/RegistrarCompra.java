package com.workshopmanagement.rdmotors.compras.aplicacion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.SinTurnoAbiertoException;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;
import com.workshopmanagement.rdmotors.compras.dominio.LineaCompra;
import com.workshopmanagement.rdmotors.compras.dominio.Proveedor;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCompras;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCuentas;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioProveedores;
import com.workshopmanagement.rdmotors.inventario.aplicacion.CrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;

/**
 * CASO DE USO — registrar una compra a proveedor.
 *
 * <p>Es el flujo del que cuelga todo el sistema: aqui entran los repuestos al inventario, aqui
 * nace su costo y aqui se fija su precio de venta. El producto no existe hasta que se compra —
 * no se importan catalogos completos de proveedor.
 *
 * <h2>Por que la transaccion se abre AQUI</h2>
 *
 * Ni en el controlador (que no sabe de negocio) ni en las entidades (que no deben saber de
 * infraestructura). El caso de uso es el dueno: define exactamente que tiene que pasar junto o no
 * pasar. Si el stock subiera y la compra no quedara guardada, el inventario reportaria mercancia
 * que nadie puede rastrear.
 *
 * <p>Bajo el mismo commit van: la compra con sus lineas, el stock de cada variante, el costo
 * promedio recalculado, el precio de venta si cambio, y los movimientos de kardex.
 *
 * <p>Cómo entra cada renglón al inventario lo sabe {@link InventarioDeCompra}, la misma pieza que
 * usan corregir y anular para deshacerlo (spec 0002).
 *
 * <h2>Fijate en lo que esta clase NO sabe</h2>
 *
 * No sabe que existe Postgres, ni HTTP, ni JSON. Solo conversa con puertos. Por eso su prueba
 * corre en milisegundos con repositorios falsos, sin levantar base de datos ni Spring.
 */
@Transactional
public class RegistrarCompra {

    private final RepositorioCompras compras;
    private final RepositorioProveedores proveedores;
    private final RepositorioCuentas cuentas;
    private final RepositorioTurnos turnos;
    private final InventarioDeCompra inventario;
    private final Reloj reloj;

    public RegistrarCompra(RepositorioCompras compras, RepositorioProveedores proveedores,
                           RepositorioCuentas cuentas, RepositorioTurnos turnos, RepositorioVariantes variantes,
                           RepositorioKardex kardex, CrearRepuesto crearRepuesto, Reloj reloj) {
        this.compras = compras;
        this.proveedores = proveedores;
        this.cuentas = cuentas;
        this.turnos = turnos;
        this.inventario = new InventarioDeCompra(variantes, kardex, compras, crearRepuesto);
        this.reloj = reloj;
    }

    public Compra ejecutar(ComandoRegistrarCompra comando) {
        comando.actor().exigirAdministrador();
        // La llave, antes que nada (spec 0009, RF-009): un doble clic o un reintento tras un corte llegan con la
        // misma y se devuelve la compra que ya existe, sin entrar la mercancía dos veces.
        Optional<Compra> yaRegistrada = compras.buscarPorLlave(comando.llave());
        if (yaRegistrada.isPresent()) {
            return yaRegistrada.get();
        }
        // Pagada con plata del cajón (spec 0006): el turno se pide primero, antes que los repuestos, como en
        // todo lo que mueve plata del turno. Así el cierre no la deja por fuera de su arqueo.
        UUID turnoDeCajaId = null;
        if (comando.pagadaDeCaja()) {
            turnoDeCajaId = turnos.abiertoParaMover()
                    .orElseThrow(() -> new SinTurnoAbiertoException("No hay un turno abierto: la compra no se puede "
                            + "registrar «con plata del cajón». Abre el turno en Vender o desmárcala."))
                    .getId();
        }

        Proveedor proveedor = proveedores.buscar(comando.proveedorId())
                .orElseThrow(() -> new ReglaDeNegocioException("El proveedor no existe"));

        // Si la forma de pago y la cuenta no cuadran (transferencia sin cuenta, efectivo con una),
        // lo rechaza Compra.registrar: la regla es suya, aqui solo se busca lo que se nombro.
        CuentaPago cuenta = comando.cuentaId() == null ? null
                : cuentas.buscar(comando.cuentaId())
                        .orElseThrow(() -> new ReglaDeNegocioException("La cuenta no existe"));

        InventarioDeCompra.exigirRepuestosDistintos(comando.lineas());

        // 1. Resolver el repuesto de cada renglon: o ya existe, o nace aqui mismo.
        List<LineaCompra> lineas = new ArrayList<>();
        for (ComandoRegistrarCompra.Linea linea : comando.lineas()) {
            lineas.add(linea.aLineaDe(inventario.resolverRepuesto(linea, comando.actor())));
        }

        // 2. La llave otra vez, con los repuestos ya bloqueados: si una compra con la misma llave llegó a la vez,
        //    este hilo esperó su bloqueo y ahora ya la ve guardada. Tiene que ser ANTES de mover inventario: más
        //    adelante los repuestos ya están cambiados en memoria y salir a medias los dejaría sumados igual.
        yaRegistrada = compras.buscarPorLlave(comando.llave());
        if (yaRegistrada.isPresent()) {
            return yaRegistrada.get();
        }

        // 3. La compra nace y con ella su id, que los movimientos de kardex necesitan para
        //    apuntar a su origen.
        Instant ahora = reloj.ahora();
        Compra compra = Compra.registrar(proveedor, comando.fechaDocumento(), ahora,
                comando.numeroFactura(), comando.formaPago(), cuenta, turnoDeCajaId, comando.actor().id(),
                comando.llave(), lineas);

        // 4. Recien ahora se mueve el inventario, renglón por renglón.
        for (LineaCompra linea : compra.getLineas()) {
            inventario.darEntrada(linea, compra.getId(), null, comando.actor().id(), ahora);
        }

        return compras.guardar(compra);
    }
}
