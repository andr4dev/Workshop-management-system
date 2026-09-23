package com.workshopmanagement.rdmotors.caja.aplicacion;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.Gasto;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioGastos;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.Motivo;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;

/**
 * CASO DE USO — anular un gasto mal registrado (spec 0006, H7 y RF-006): se escribió $150.000 en vez de
 * $15.000. Queda en la lista, tachado, con su motivo, y deja de restar.
 *
 * <p><b>Uno del cajón solo mientras su turno siga abierto</b>: el arqueo de un turno cerrado ya se firmó.
 * El turno se bloquea antes que el gasto, como en todo lo que mueve plata del turno.
 *
 * <p><b>Uno por fuera, en cualquier momento</b> (plan 0006, decisión 11): no está en ningún arqueo.
 */
@Transactional
public class AnularGasto {

    static final String TURNO_CERRADO = "Ese turno ya se cerró: su arqueo no se modifica";

    private final RepositorioGastos gastos;
    private final RepositorioTurnos turnos;
    private final RepositorioAuditoria auditoria;
    private final Reloj reloj;

    public AnularGasto(RepositorioGastos gastos, RepositorioTurnos turnos, RepositorioAuditoria auditoria,
                       Reloj reloj) {
        this.gastos = gastos;
        this.turnos = turnos;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public Gasto ejecutar(UUID gastoId, String motivo, Actor actor) {
        String motivoLimpio = Motivo.exigir(motivo);
        if (actor == null) {
            throw new ReglaDeNegocioException("Falta el usuario que anula");
        }
        UUID usuarioId = actor.id();
        // Sin bloqueo, solo para saber si es del cajón: un gasto no cambia de turno ni de origen.
        Gasto sinBloquear = gastos.buscar(gastoId)
                .orElseThrow(() -> new ReglaDeNegocioException("El gasto no existe"));
        if (sinBloquear.isDelCajon()) {
            TurnoCaja turnoAbierto = turnos.abiertoParaMover().orElse(null);
            if (turnoAbierto == null || !sinBloquear.getTurnoId().equals(turnoAbierto.getId())) {
                throw new ReglaDeNegocioException(TURNO_CERRADO);
            }
            turnoAbierto.exigirQuePuedaOperar(actor);
        } else {
            actor.exigirAdministrador();
        }

        Gasto gasto = gastos.buscarParaModificar(gastoId)
                .orElseThrow(() -> new ReglaDeNegocioException("El gasto no existe"));
        Map<String, Object> antes = gasto.fotografia();
        Instant ahora = reloj.ahora();
        gasto.anular(motivoLimpio, usuarioId, ahora);
        auditoria.registrar(EventoAuditoria.nuevo(ahora, usuarioId, AccionAuditada.ANULAR_GASTO, Gasto.TIPO_AUDITORIA,
                gastoId, antes, gasto.fotografia(), motivoLimpio));
        return gastos.guardar(gasto);
    }
}
