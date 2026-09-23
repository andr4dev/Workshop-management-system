package com.workshopmanagement.rdmotors.clientes.aplicacion;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.clientes.dominio.Abono;
import com.workshopmanagement.rdmotors.clientes.dominio.CarteraDelCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioAbonos;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioClientes;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioDeudas;
import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.Motivo;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;

/**
 * CASO DE USO — anular un abono mal registrado (spec 0008, RF-017): lo que pagó vuelve a deberse.
 *
 * <p>Es del <b>administrador</b> (decisión 5). Si entró en efectivo, solo mientras su turno siga abierto: ese cajón ya
 * se contó y se firmó, como un gasto (spec 0006). Por transferencia no tocó ningún cajón, así que se puede después.
 */
@Transactional
public class AnularAbono {

    public static final String TURNO_CERRADO = "Ese turno ya se cerró: el abono quedó en su arqueo";

    private final RepositorioAbonos abonos;
    private final RepositorioClientes clientes;
    private final RepositorioDeudas deudas;
    private final RepositorioTurnos turnos;
    private final RepositorioAuditoria auditoria;
    private final Reloj reloj;

    public AnularAbono(RepositorioAbonos abonos, RepositorioClientes clientes, RepositorioDeudas deudas,
                       RepositorioTurnos turnos, RepositorioAuditoria auditoria, Reloj reloj) {
        this.abonos = abonos;
        this.clientes = clientes;
        this.deudas = deudas;
        this.turnos = turnos;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public Abono ejecutar(UUID abonoId, String motivo, Actor actor) {
        String motivoLimpio = Motivo.exigir(motivo);
        if (actor == null) {
            throw new ReglaDeNegocioException("Falta el usuario que anula");
        }
        actor.exigirAdministrador();
        Abono abono = abonos.buscar(abonoId)
                .orElseThrow(() -> new ReglaDeNegocioException("Ese abono no existe"));
        if (abono.entraAlCajon()) {
            TurnoCaja turnoAbierto = turnos.abiertoParaMover().orElse(null);
            if (turnoAbierto == null || !turnoAbierto.getId().equals(abono.getTurnoId())) {
                throw new ReglaDeNegocioException(TURNO_CERRADO);
            }
        }
        Cliente cliente = clientes.buscarParaModificar(abono.getClienteId())
                .orElseThrow(() -> new ReglaDeNegocioException("El cliente de ese abono no existe"));

        CarteraDelCliente cartera = new CarteraDelCliente(cliente, deudas.delCliente(cliente.getId()),
                abonos.delCliente(cliente.getId()));
        Abono enLaCartera = cartera.abonos().stream().filter(a -> a.getId().equals(abonoId)).findFirst().orElseThrow();
        Map<String, Object> antes = enLaCartera.fotografia();
        Instant ahora = reloj.ahora();
        cartera.anularAbono(enLaCartera, motivoLimpio, actor.id(), ahora);

        auditoria.registrar(EventoAuditoria.nuevo(ahora, actor.id(), AccionAuditada.ANULAR_ABONO, Abono.TIPO_AUDITORIA,
                abonoId, antes, enLaCartera.fotografia(), motivoLimpio));
        cartera.deudas().forEach(deudas::guardar);
        cartera.abonos().forEach(abonos::guardar);
        return enLaCartera;
    }
}
