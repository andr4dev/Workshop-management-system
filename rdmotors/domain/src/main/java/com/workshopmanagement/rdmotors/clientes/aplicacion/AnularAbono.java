package com.workshopmanagement.rdmotors.clientes.aplicacion;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
 *
 * <p><b>Un abono mixto se anula entero</b> (spec 0017): pedido por cualquiera de sus dos partes, se anulan las dos, con
 * el mismo motivo. Como tiene una parte en efectivo, solo mientras su turno siga abierto.
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
        if (abono.estaAnulado()) {
            throw new ReglaDeNegocioException("Este abono ya fue anulado");
        }
        // El turno antes que el cliente, en el mismo orden en que se bloquean al abonar. Un mixto tiene una parte en
        // efectivo, y sus dos partes son del mismo turno: pedido por la transferencia también lo necesita abierto.
        TurnoCaja turnoAbierto = null;
        if (abono.entraAlCajon() || abono.esParteDeUnPago()) {
            turnoAbierto = turnos.abiertoParaMover().orElse(null);
            if (turnoAbierto == null || !turnoAbierto.getId().equals(abono.getTurnoId())) {
                throw new ReglaDeNegocioException(TURNO_CERRADO);
            }
        }
        Cliente cliente = clientes.buscarParaModificar(abono.getClienteId())
                .orElseThrow(() -> new ReglaDeNegocioException("El cliente de ese abono no existe"));

        CarteraDelCliente cartera = new CarteraDelCliente(cliente, deudas.delCliente(cliente.getId()),
                abonos.delCliente(cliente.getId()));
        Abono enLaCartera = cartera.abonos().stream().filter(a -> a.getId().equals(abonoId)).findFirst().orElseThrow();
        // Un mixto, entero: las partes vigentes de su pago.
        List<Abono> partes = enLaCartera.esParteDeUnPago()
                ? cartera.abonos().stream()
                        .filter(a -> enLaCartera.getPagoId().equals(a.getPagoId()) && !a.estaAnulado())
                        .toList()
                : List.of(enLaCartera);
        for (Abono parte : partes) {
            if (parte.entraAlCajon() && (turnoAbierto == null || !turnoAbierto.getId().equals(parte.getTurnoId()))) {
                throw new ReglaDeNegocioException(TURNO_CERRADO);
            }
        }
        Map<Abono, Map<String, Object>> antes = new LinkedHashMap<>();
        partes.forEach(parte -> antes.put(parte, parte.fotografia()));
        Instant ahora = reloj.ahora();
        cartera.anularAbonos(partes, motivoLimpio, actor.id(), ahora);

        antes.forEach((parte, foto) -> auditoria.registrar(EventoAuditoria.nuevo(ahora, actor.id(),
                AccionAuditada.ANULAR_ABONO, Abono.TIPO_AUDITORIA, parte.getId(), foto, parte.fotografia(),
                motivoLimpio)));
        cartera.deudas().forEach(deudas::guardar);
        cartera.abonos().forEach(abonos::guardar);
        return enLaCartera;
    }
}
