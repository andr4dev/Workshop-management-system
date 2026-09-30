package com.workshopmanagement.rdmotors.clientes.aplicacion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.UUID;

import com.workshopmanagement.rdmotors.clientes.dominio.RepartoDelFiado;
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

    /** Bloqueado, y con el fiado abierto: es lo único que hace falta para fiarle (la cédula y el celular no). */
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
     * Un renglón de la venta fiada, lo que la cartera necesita de él (spec 0016).
     *
     * @param total       el del renglón antes del descuento
     * @param descripcion el nombre del producto
     */
    public record RenglonFiado(UUID lineaVentaId, UUID varianteId, int posicion, String descripcion, Dinero total) {
    }

    /**
     * Las deudas de una venta fiada, ya guardada la venta: <b>una por producto</b> con lo que quedó debiendo de él
     * ({@link RepartoDelFiado}). Un producto que quedó pago no deja deuda. Si el cliente tenía algo a favor, se aplica.
     *
     * @param cliente     el de {@link #clienteParaFiar}, bloqueado
     * @param dia         el día de Colombia en que se cobró
     * @param renglones   los de la venta, en su orden
     * @param pagaPrimero los repuestos (por variante) que el cajero marcó como pagados al llevárselos, en su orden
     */
    public List<Deuda> registrar(Cliente cliente, UUID ventaId, long numeroVenta, LocalDate dia,
                                 List<RenglonFiado> renglones, Dinero descuento, Dinero fiado, List<UUID> pagaPrimero,
                                 UUID registradaPorId, Instant cuando) {
        List<RenglonFiado> enOrden = renglones.stream().sorted(Comparator.comparingInt(RenglonFiado::posicion)).toList();
        List<Integer> marcados = new ArrayList<>();
        for (UUID varianteId : pagaPrimero) {
            int i = IntStream.range(0, enOrden.size()).filter(k -> enOrden.get(k).varianteId().equals(varianteId))
                    .findFirst().orElseThrow(() -> new ReglaDeNegocioException(
                            "Lo que se paga ahora tiene que ser de esta venta"));
            marcados.add(i);
        }
        List<Dinero> fiados = RepartoDelFiado.porRenglon(enOrden.stream().map(RenglonFiado::total).toList(),
                descuento, fiado, marcados);

        List<Deuda> nuevas = new ArrayList<>();
        for (int i = 0; i < enOrden.size(); i++) {
            if (fiados.get(i).esCero()) {
                continue;
            }
            RenglonFiado r = enOrden.get(i);
            nuevas.add(Deuda.porProducto(cliente.getId(), ventaId, numeroVenta, r.lineaVentaId(), r.posicion(),
                    r.descripcion(), dia, fiados.get(i), registradaPorId, cuando));
        }
        CarteraDelCliente cartera = carteraDe(cliente);
        cartera.registrarDeudas(nuevas, cuando);
        List<Deuda> guardadas = nuevas.stream().map(deudas::guardar).toList();
        cartera.abonos().forEach(abonos::guardar);
        return guardadas;
    }

    /**
     * La venta fiada se anuló: su deuda también, y lo que se le había abonado pasa a las otras deudas del cliente o
     * le queda a favor (decisión 6). No sale plata del cajón por la parte fiada.
     */
    public void alAnular(UUID ventaId, UUID clienteId, UUID anuladaPorId, Instant cuando) {
        Cliente cliente = clientes.buscarParaModificar(clienteId)
                .orElseThrow(() -> new ReglaDeNegocioException("El cliente de esa venta no existe"));
        List<UUID> deLaVenta = deudas.deLaVenta(ventaId).stream().map(Deuda::getId).toList();
        if (deLaVenta.isEmpty()) {
            throw new IllegalStateException("La venta fiada " + ventaId + " no tiene su deuda");
        }
        CarteraDelCliente cartera = carteraDe(cliente);
        // Todas las de la venta (una por producto, spec 0016), de una vez: ver CarteraDelCliente.anularDeudas.
        cartera.anularDeudas(cartera.deudas().stream().filter(d -> deLaVenta.contains(d.getId())).toList(),
                anuladaPorId, cuando);
        cartera.deudas().forEach(deudas::guardar);
        cartera.abonos().forEach(abonos::guardar);
    }

    private CarteraDelCliente carteraDe(Cliente cliente) {
        return new CarteraDelCliente(cliente, deudas.delCliente(cliente.getId()), abonos.delCliente(cliente.getId()));
    }
}
