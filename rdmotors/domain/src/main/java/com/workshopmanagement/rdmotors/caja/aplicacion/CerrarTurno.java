package com.workshopmanagement.rdmotors.caja.aplicacion;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.ArqueoDeTurno;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoYaCerradoException;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.correo.aplicacion.EncolarCorreoDelCierre;

/**
 * CASO DE USO — cerrar el turno con lo contado (spec 0006, H3, RF-012 a RF-018).
 *
 * <h2>En orden</h2>
 * <ol>
 *   <li><b>Bloquear el turno en exclusiva.</b> Espera a que terminen los cobros, anulaciones, gastos, retiros y
 *       compras de caja en curso, que lo tienen compartido. Los que lleguen después esperan a este cierre y
 *       encuentran el turno cerrado. Así ninguno queda en un turno cerrado sin contar (RF-017).</li>
 *   <li><b>Exigir que siga abierto.</b> Un doble clic espera al primero y aquí se entera de que ya se cerró
 *       (RF-018).</li>
 *   <li><b>Calcular lo que debería haber</b>, con todo lo que ya entró, y cerrar con lo contado.</li>
 *   <li><b>Si no cuadra, dejar el evento de auditoría</b> con las cifras y quién cerró (RF-015).</li>
 * </ol>
 */
@Transactional
public class CerrarTurno {

    public static final String TIPO_AUDITORIA = "TURNO_CAJA";

    private final RepositorioTurnos turnos;
    private final CalcularArqueo arqueo;
    private final RepositorioAuditoria auditoria;
    private final EncolarCorreoDelCierre correoDelCierre;
    private final Reloj reloj;

    public CerrarTurno(RepositorioTurnos turnos, CalcularArqueo arqueo, RepositorioAuditoria auditoria,
                       EncolarCorreoDelCierre correoDelCierre, Reloj reloj) {
        this.turnos = turnos;
        this.arqueo = arqueo;
        this.auditoria = auditoria;
        this.correoDelCierre = correoDelCierre;
        this.reloj = reloj;
    }

    public TurnoCaja ejecutar(UUID turnoId, Dinero contado, Actor actor) {
        TurnoCaja.exigirContado(contado);
        if (actor == null) {
            throw new ReglaDeNegocioException("Falta quién cierra el turno");
        }
        UUID usuarioId = actor.id();
        TurnoCaja turno = turnos.buscarParaCerrar(turnoId)
                .orElseThrow(() -> new ReglaDeNegocioException("El turno no existe"));
        turno.exigirQuePuedaOperar(actor);
        if (!turno.estaAbierto()) {
            throw new TurnoYaCerradoException(turno.getId(), turno.getCerradoEn(), turno.getCerradoPorId());
        }

        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("estado", turno.getEstado().name());
        antes.put("fondo", turno.getFondo().valor().longValueExact());

        ArqueoDeTurno calculado = arqueo.de(turno);
        Instant ahora = reloj.ahora();
        turno.cerrar(calculado, contado, usuarioId, ahora);

        if (turno.tieneDiferencia()) {
            auditoria.registrar(EventoAuditoria.nuevo(ahora, usuarioId, AccionAuditada.CERRAR_CAJA_CON_DIFERENCIA,
                    TIPO_AUDITORIA, turno.getId(), antes, turno.fotografiaDelCierre(), null));
        }
        TurnoCaja cerrado = turnos.guardar(turno);
        // El resumen para el dueño (spec 0010): queda por mandar en este mismo commit. Mandarlo es de la tarea, que
        // no le hace esperar a nadie ni necesita internet ahora.
        correoDelCierre.alCerrar(cerrado.getId(), ahora);
        return cerrado;
    }
}
