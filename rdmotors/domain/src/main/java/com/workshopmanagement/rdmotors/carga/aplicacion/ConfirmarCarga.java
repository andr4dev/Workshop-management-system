package com.workshopmanagement.rdmotors.carga.aplicacion;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.carga.dominio.CargaCambioException;
import com.workshopmanagement.rdmotors.carga.dominio.CargaDeInventario;
import com.workshopmanagement.rdmotors.carga.dominio.EstadoCarga;
import com.workshopmanagement.rdmotors.carga.dominio.Problema;
import com.workshopmanagement.rdmotors.carga.dominio.RenglonDeCarga;
import com.workshopmanagement.rdmotors.carga.dominio.RepartoDelIva;
import com.workshopmanagement.rdmotors.carga.dominio.Revision;
import com.workshopmanagement.rdmotors.carga.dominio.Revision.RenglonRevisado;
import com.workshopmanagement.rdmotors.carga.dominio.puerto.RepositorioCargas;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compras.aplicacion.ComandoRegistrarCompra;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarCompra;
import com.workshopmanagement.rdmotors.compras.dominio.Compra;
import com.workshopmanagement.rdmotors.compras.dominio.LineaCompra;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCompras;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCuentas;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioProveedores;
import com.workshopmanagement.rdmotors.inventario.aplicacion.ComandoCrearRepuesto;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioCategorias;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioVariantes;

/**
 * CASO DE USO — confirmar la pre-carga: todo entra al inventario como <b>una</b> compra (spec 0012, H4, RF-015 a
 * RF-018).
 *
 * <h2>No se reimplementa la compra: se usa</h2>
 *
 * Cada renglón entra por su total con IVA —el modo "me llegaron 15 y pagué $200.000" de siempre—, los nuevos con su
 * repuesto adentro y las reposiciones con el suyo. Así la carga hereda todo lo que ya tienen las compras: el costo
 * promedio, el kardex, corregir, anular, el historial del proveedor.
 *
 * <h2>Entra todo o nada, y una sola vez</h2>
 *
 * La compra se registra dentro de esta misma transacción: o entra la compra y la carga queda confirmada, o no pasa
 * ninguna de las dos (RF-016). Su llave contra el doble registro es <b>el id de la carga</b>: confirmar dos veces, o
 * desde dos equipos a la vez, lo resuelve la compra misma y devuelve la que ya existe (RF-017). Y la carga está
 * bloqueada mientras tanto: el segundo que confirma espera al primero y la encuentra ya confirmada.
 */
@Transactional
public class ConfirmarCarga {

    private final RepositorioCargas cargas;
    private final RepositorioCompras compras;
    private final RegistrarCompra registrarCompra;
    private final ArmadoDelDetalle detalle;
    private final Reloj reloj;

    public ConfirmarCarga(RepositorioCargas cargas, RepositorioCompras compras, RegistrarCompra registrarCompra,
                          RepositorioVariantes variantes, RepositorioCategorias categorias,
                          RepositorioProveedores proveedores, RepositorioCuentas cuentas, Reloj reloj) {
        this.cargas = cargas;
        this.compras = compras;
        this.registrarCompra = registrarCompra;
        this.detalle = new ArmadoDelDetalle(variantes, categorias, proveedores, cuentas);
        this.reloj = reloj;
    }

    /**
     * @param reposicionesVistas cuántas reposiciones mostraba la pantalla al apretar "confirmar". Si ahora son otras
     *                           —alguien creó uno de esos códigos a mano mientras tanto—, se avisa antes de registrar
     *                           nada
     */
    public ResultadoConfirmacion ejecutar(UUID id, int reposicionesVistas, Actor actor) {
        actor.exigirAdministrador();
        CargaDeInventario carga = cargas.buscarParaModificar(id)
                .orElseThrow(() -> new ReglaDeNegocioException("Esa carga no existe"));
        if (carga.getEstado() == EstadoCarga.CONFIRMADA) {
            // Un reintento tras un corte, o el segundo de dos equipos: ya entró, y se devuelve lo que entró.
            return ResultadoConfirmacion.de(carga, compras.buscar(carga.getCompraId()).orElseThrow());
        }
        carga.exigirBorrador();

        Revision revision = detalle.armar(carga).revision();
        if (!revision.sePuedeConfirmar()) {
            throw new ReglaDeNegocioException("Todavía no se puede confirmar: " + revision.problemas().stream()
                    .map(Problema::mensaje).collect(Collectors.joining("; ")));
        }
        int reposiciones = revision.totales().reposiciones();
        if (reposiciones != reposicionesVistas) {
            throw new CargaCambioException(reposiciones > reposicionesVistas
                    ? "Mientras revisabas, alguien creó a mano " + cuantos(reposiciones - reposicionesVistas)
                            + " de estos códigos: ahora " + (reposiciones - reposicionesVistas == 1 ? "es reposición"
                            : "son reposiciones") + " y suma al stock que ya tiene. Revisa la pre-carga y confirma "
                            + "de nuevo."
                    : "El inventario cambió mientras revisabas. Revisa la pre-carga y confirma de nuevo.");
        }

        Compra compra = registrarCompra.ejecutar(comandoDeCompra(carga, revision, actor));
        carga.confirmar(compra.getId(), actor.id(), reloj.ahora());
        cargas.guardar(carga);
        return ResultadoConfirmacion.de(carga, compra);
    }

    /**
     * La compra que la carga describe: los renglones que no se quitaron, cada uno por su total con su parte del IVA,
     * repartida para que sumen exactamente lo que se pagó (RF-004a).
     */
    private static ComandoRegistrarCompra comandoDeCompra(CargaDeInventario carga, Revision revision, Actor actor) {
        List<RenglonRevisado> incluidos = revision.renglones().stream().filter(r -> !r.renglon().isQuitado())
                .toList();
        List<Dinero> conIva = RepartoDelIva.conIva(
                incluidos.stream().map(r -> r.renglon().getValorTotal()).toList(), revision.totales().iva());

        List<ComandoRegistrarCompra.Linea> lineas = new ArrayList<>(incluidos.size());
        for (int i = 0; i < incluidos.size(); i++) {
            RenglonRevisado r = incluidos.get(i);
            RenglonDeCarga renglon = r.renglon();
            if (r.esReposicion()) {
                lineas.add(ComandoRegistrarCompra.Linea.porTotal(r.existente().getId(), renglon.getCantidad(),
                        conIva.get(i), renglon.isAplicarPrecioNuevo() ? renglon.getPrecioFinal() : null));
            } else {
                // Stock mínimo 0: la factura no dice cuánto hay que tener, y un mínimo inventado encendería avisos
                // de "stock bajo" en 600 repuestos a la vez.
                ComandoCrearRepuesto nuevo = ComandoCrearRepuesto.conConceptoNuevo(renglon.getNombre(),
                        renglon.getCategoriaId(), null, renglon.getCodigo(), renglon.getMarca(),
                        renglon.getPrecioFinal(), 0);
                lineas.add(ComandoRegistrarCompra.Linea.porTotalCreando(nuevo, renglon.getCantidad(), conIva.get(i)));
            }
        }
        return new ComandoRegistrarCompra(carga.getProveedorId(), carga.getFechaFactura(), carga.getNumeroFactura(),
                carga.getFormaPago(), carga.getCuentaId(), actor, lineas, false, carga.getId());
    }

    private static String cuantos(int n) {
        return n == 1 ? "uno" : String.valueOf(n);
    }

    /** Lo que entró: para el resumen de la pantalla y para ir a la compra. */
    public record ResultadoConfirmacion(UUID cargaId, UUID compraId, Dinero total, int renglones, int unidades) {

        static ResultadoConfirmacion de(CargaDeInventario carga, Compra compra) {
            List<LineaCompra> lineas = compra.lineasVigentes();
            return new ResultadoConfirmacion(carga.getId(), compra.getId(), compra.getTotal(), lineas.size(),
                    lineas.stream().mapToInt(LineaCompra::getCantidad).sum());
        }
    }
}
