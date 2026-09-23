package com.workshopmanagement.rdmotors.caja.aplicacion;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.Retiro;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioRetiros;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.Motivo;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;

/**
 * CASO DE USO — anular un retiro mal registrado (spec 0006, H7 y RF-006), solo mientras su turno siga
 * abierto. Queda tachado, con su motivo, y deja de restar.
 */
@Transactional
public class AnularRetiro {

    private final RepositorioRetiros retiros;
    private final RepositorioTurnos turnos;
    private final RepositorioAuditoria auditoria;
    private final Reloj reloj;

    public AnularRetiro(RepositorioRetiros retiros, RepositorioTurnos turnos, RepositorioAuditoria auditoria,
                        Reloj reloj) {
        this.retiros = retiros;
        this.turnos = turnos;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public Retiro ejecutar(UUID retiroId, String motivo, Actor actor) {
        String motivoLimpio = Motivo.exigir(motivo);
        if (actor == null) {
            throw new ReglaDeNegocioException("Falta el usuario que anula");
        }
        UUID usuarioId = actor.id();
        TurnoCaja turnoAbierto = turnos.abiertoParaMover().orElse(null);
        Retiro retiro = retiros.buscarParaModificar(retiroId)
                .orElseThrow(() -> new ReglaDeNegocioException("El retiro no existe"));
        if (turnoAbierto == null || !retiro.getTurnoId().equals(turnoAbierto.getId())) {
            throw new ReglaDeNegocioException(AnularGasto.TURNO_CERRADO);
        }
        turnoAbierto.exigirQuePuedaOperar(actor);

        Map<String, Object> antes = retiro.fotografia();
        Instant ahora = reloj.ahora();
        retiro.anular(motivoLimpio, usuarioId, ahora);
        auditoria.registrar(EventoAuditoria.nuevo(ahora, usuarioId, AccionAuditada.ANULAR_RETIRO,
                Retiro.TIPO_AUDITORIA, retiroId, antes, retiro.fotografia(), motivoLimpio));
        return retiros.guardar(retiro);
    }
}
