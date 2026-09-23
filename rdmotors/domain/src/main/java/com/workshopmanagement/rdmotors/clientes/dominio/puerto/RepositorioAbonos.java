package com.workshopmanagement.rdmotors.clientes.dominio.puerto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.workshopmanagement.rdmotors.clientes.dominio.Abono;

/** PUERTO — los abonos de los clientes y su numeración (spec 0008). */
public interface RepositorioAbonos {

    /**
     * El siguiente número de recibo, <b>dentro de la transacción del abono</b>: si el abono se deshace, el número
     * vuelve a estar libre, como el de las ventas.
     */
    long siguienteNumero();

    /** Todos los del cliente, anulados incluidos, con sus aplicaciones. */
    List<Abono> delCliente(UUID clienteId);

    Optional<Abono> buscar(UUID id);

    Optional<Abono> buscarPorLlave(UUID llave);

    /** Los recibidos con ese turno abierto, anulados incluidos: el arqueo decide qué entra al cajón. */
    List<Abono> delTurno(UUID turnoId);

    /**
     * Si la base ya tiene un abono con la misma llave (dos a la vez), lanza
     * {@link com.workshopmanagement.rdmotors.caja.dominio.MovimientoRepetidoException}.
     */
    Abono guardar(Abono abono);
}
