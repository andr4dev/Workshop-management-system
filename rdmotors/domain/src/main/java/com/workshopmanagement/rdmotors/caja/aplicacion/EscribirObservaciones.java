package com.workshopmanagement.rdmotors.caja.aplicacion;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * CASO DE USO — explicar un cierre que no cuadró (spec 0006, decisión 2 y RF-014). Se escribe al cerrar, o
 * después desde el historial si el cajero cerró la pantalla sin hacerlo. Una sola vez.
 *
 * <p>El turno se bloquea: dos pestañas escribiendo a la vez dejan una sola explicación.
 */
@Transactional
public class EscribirObservaciones {

    private final RepositorioTurnos turnos;

    public EscribirObservaciones(RepositorioTurnos turnos) {
        this.turnos = turnos;
    }

    public TurnoCaja ejecutar(UUID turnoId, String observaciones, Actor actor) {
        TurnoCaja turno = turnos.buscarParaCerrar(turnoId)
                .orElseThrow(() -> new ReglaDeNegocioException("El turno no existe"));
        turno.exigirQuePuedaOperar(actor);
        turno.escribirObservaciones(observaciones);
        return turnos.guardar(turno);
    }
}
