package com.workshopmanagement.rdmotors.caja.dominio.puerto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.workshopmanagement.rdmotors.caja.dominio.Retiro;

/** PUERTO — los retiros de plata del cajón. */
public interface RepositorioRetiros {

    /**
     * Si la base ya tiene un retiro con la misma llave, lanza
     * {@link com.workshopmanagement.rdmotors.caja.dominio.MovimientoRepetidoException}.
     */
    Retiro guardar(Retiro retiro);

    Optional<Retiro> buscar(UUID id);

    Optional<Retiro> buscarPorLlave(UUID llave);

    /** Con intención de anularlo: el adaptador toma bloqueo de fila. */
    Optional<Retiro> buscarParaModificar(UUID id);

    /** Los del turno, anulados incluidos, en el orden en que se registraron. */
    List<Retiro> delTurno(UUID turnoId);
}
