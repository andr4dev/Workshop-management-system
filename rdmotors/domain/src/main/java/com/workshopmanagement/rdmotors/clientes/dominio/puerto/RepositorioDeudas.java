package com.workshopmanagement.rdmotors.clientes.dominio.puerto;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.workshopmanagement.rdmotors.clientes.dominio.Deuda;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/** PUERTO — lo que deben los clientes, una deuda por venta fiada o por el saldo del cuaderno (spec 0008). */
public interface RepositorioDeudas {

    /** Todas las del cliente, anuladas incluidas. */
    List<Deuda> delCliente(UUID clienteId);

    Optional<Deuda> deLaVenta(UUID ventaId);

    /** Las de esas ventas: el comprobante dice cuánto quedó debiendo el cliente después de cada una. */
    List<Deuda> deLasVentas(Collection<UUID> ventaIds);

    /** Cuánto debe cada uno de esos clientes: la suma de lo pendiente de sus deudas. Los que no deben, no salen. */
    Map<UUID, Dinero> debeDe(Collection<UUID> clienteIds);

    Deuda guardar(Deuda deuda);
}
