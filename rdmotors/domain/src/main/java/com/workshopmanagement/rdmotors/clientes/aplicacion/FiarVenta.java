package com.workshopmanagement.rdmotors.clientes.aplicacion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.workshopmanagement.rdmotors.clientes.dominio.CarteraDelCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.Deuda;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioAbonos;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioClientes;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioDeudas;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * Lo que la venta le pide a la cartera (spec 0008, H1 y H9): el cliente al que se le fía, la deuda que deja la venta,
 * y deshacerla si la venta se anula. Es como ventas habla con clientes (skill backend: por su caso de uso, no por su
 * repositorio).
 *
 * <p><b>No abre transacción</b>, como {@code CalcularArqueo}: lo llaman cobrar y anular, dentro de la suya. La deuda
 * nace y muere en el mismo commit que la venta.
 *
 * <p><b>El cliente se bloquea al final</b>, después del turno y de los repuestos (plan 0008, decisión 4): así un
 * cobro, un abono y una anulación del mismo cliente se esperan en fila y ninguno se traba con otro.
 */
public class FiarVenta {

    private final RepositorioClientes clientes;
    private final RepositorioDeudas deudas;
    private final RepositorioAbonos abonos;

    public FiarVenta(RepositorioClientes clientes, RepositorioDeudas deudas, RepositorioAbonos abonos) {
        this.clientes = clientes;
        this.deudas = deudas;
        this.abonos = abonos;
    }

    /** Bloqueado, y con lo que hace falta para fiarle: los datos completos y el fiado abierto. */
    public Cliente clienteParaFiar(UUID clienteId) {
        Cliente cliente = clientes.buscarParaModificar(clienteId)
                .orElseThrow(() -> new ReglaDeNegocioException("Ese cliente no existe"));
        cliente.exigirQueSePuedaFiar();
        return cliente;
    }

    /** Una venta de contado a nombre de alguien (RF-023): solo tiene que existir. */
    public Cliente clienteDeLaVenta(UUID clienteId) {
        return clientes.buscar(clienteId).orElseThrow(() -> new ReglaDeNegocioException("Ese cliente no existe"));
    }

    /**
     * La deuda de una venta fiada, ya guardada la venta. Si el cliente tenía algo a favor, se le aplica.
     *
     * @param cliente el de {@link #clienteParaFiar}, bloqueado
     * @param dia     el día de Colombia en que se cobró
     */
    public Deuda registrar(Cliente cliente, UUID ventaId, long numeroVenta, LocalDate dia, Dinero fiado,
                           UUID registradaPorId, Instant cuando) {
        CarteraDelCliente cartera = carteraDe(cliente);
        Deuda deuda = Deuda.porVenta(cliente.getId(), ventaId, numeroVenta, dia, fiado, registradaPorId, cuando);
        cartera.registrarDeuda(deuda, cuando);
        Deuda guardada = deudas.guardar(deuda);
        cartera.abonos().forEach(abonos::guardar);
        return guardada;
    }

    /**
     * La venta fiada se anuló: su deuda también, y lo que se le había abonado pasa a las otras deudas del cliente o
     * le queda a favor (decisión 6). No sale plata del cajón por la parte fiada.
     */
    public void alAnular(UUID ventaId, UUID clienteId, UUID anuladaPorId, Instant cuando) {
        Cliente cliente = clientes.buscarParaModificar(clienteId)
                .orElseThrow(() -> new ReglaDeNegocioException("El cliente de esa venta no existe"));
        Deuda deuda = deudas.deLaVenta(ventaId)
                .orElseThrow(() -> new IllegalStateException("La venta fiada " + ventaId + " no tiene su deuda"));
        CarteraDelCliente cartera = carteraDe(cliente);
        Deuda enLaCartera = cartera.deudas().stream().filter(d -> d.getId().equals(deuda.getId())).findFirst()
                .orElseThrow();
        cartera.anularDeuda(enLaCartera, anuladaPorId, cuando);
        cartera.deudas().forEach(deudas::guardar);
        cartera.abonos().forEach(abonos::guardar);
    }

    private CarteraDelCliente carteraDe(Cliente cliente) {
        return new CarteraDelCliente(cliente, deudas.delCliente(cliente.getId()), abonos.delCliente(cliente.getId()));
    }
}
