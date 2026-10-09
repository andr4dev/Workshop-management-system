package com.workshopmanagement.rdmotors.caja.aplicacion;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * CASO DE USO — anotar con cuánto arrancó la cuenta de transferencias en un turno que se abrió sin eso (2026-10-09).
 * Pasa con los turnos abiertos antes de que existiera, y con uno que se abrió sin mirar la cuenta. Se anota una vez.
 *
 * <p>El turno se pide bloqueado, como para cerrarlo: así el cierre no lo lee a medias.
 */
@Transactional
public class AnotarSaldoTransferencias {

    private final RepositorioTurnos turnos;

    public AnotarSaldoTransferencias(RepositorioTurnos turnos) {
        this.turnos = turnos;
    }

    public TurnoCaja ejecutar(UUID turnoId, Dinero saldo, Actor actor) {
        if (actor == null) {
            throw new ReglaDeNegocioException("Falta quién anota el saldo");
        }
        TurnoCaja turno = turnos.buscarParaCerrar(turnoId)
                .orElseThrow(() -> new ReglaDeNegocioException("El turno no existe"));
        turno.exigirQuePuedaOperar(actor);
        turno.anotarSaldoTransferencias(saldo);
        return turnos.guardar(turno);
    }
}
