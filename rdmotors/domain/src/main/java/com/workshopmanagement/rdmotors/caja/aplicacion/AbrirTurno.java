package com.workshopmanagement.rdmotors.caja.aplicacion;


import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoYaAbiertoException;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;

/**
 * CASO DE USO — abrir el turno de caja (spec 0003, RF-001). Sin turno abierto no se vende.
 *
 * <p><b>Solo puede haber un turno abierto.</b> Se revisa aquí para poder decir desde cuándo está
 * abierto el que ya existe. Pero esta revisión no alcanza sola: dos aperturas simultáneas pasarían
 * las dos. La garantía de verdad es el índice único de la base, y el adaptador traduce ese choque a
 * la misma excepción.
 */
@Transactional
public class AbrirTurno {

    private final RepositorioTurnos turnos;
    private final Reloj reloj;

    public AbrirTurno(RepositorioTurnos turnos, Reloj reloj) {
        this.turnos = turnos;
        this.reloj = reloj;
    }

    public TurnoCaja ejecutar(Dinero fondo, Actor actor) {
        if (actor == null) {
            throw new ReglaDeNegocioException("Falta quién abre el turno");
        }
        turnos.abierto().ifPresent(abierto -> {
            throw new TurnoYaAbiertoException(abierto.getAbiertoEn(), abierto.getAbiertoPorId());
        });
        return turnos.guardar(TurnoCaja.abrir(fondo, actor.id(), reloj.ahora()));
    }
}
