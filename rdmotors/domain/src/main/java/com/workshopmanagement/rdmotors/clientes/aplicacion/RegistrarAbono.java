package com.workshopmanagement.rdmotors.clientes.aplicacion;

import java.time.Instant;
import java.util.Optional;
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
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.FormaPago;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;

/**
 * CASO DE USO — el cliente abona lo que trae (spec 0008, H4, RF-011 a RF-014).
 *
 * <h2>En orden, y cada paso con su porqué</h2>
 * <ol>
 *   <li><b>La llave.</b> Si ya se abonó con esta llave (doble clic, reintento tras un corte), se devuelve ese abono.</li>
 *   <li><b>El turno, bloqueado antes que nada</b>, si lo hay: el efectivo entra a su cajón, y el cierre no puede
 *       calcularse en medio de un abono. Sin turno abierto no se recibe efectivo; por transferencia sí.</li>
 *   <li><b>El cliente, bloqueado</b> (plan 0008, decisión 4): dos abonos a la vez al mismo cliente se hacen en fila y
 *       nunca lo dejan debiendo menos de cero.</li>
 *   <li><b>La llave otra vez</b>, ya con los bloqueos: si el otro abono ganó, este devuelve el suyo.</li>
 *   <li>Se reparte: a lo más viejo, o primero a la venta que el cliente diga.</li>
 * </ol>
 */
@Transactional
public class RegistrarAbono {

    private final RepositorioAbonos abonos;
    private final RepositorioClientes clientes;
    private final RepositorioDeudas deudas;
    private final RepositorioTurnos turnos;
    private final Reloj reloj;

    public RegistrarAbono(RepositorioAbonos abonos, RepositorioClientes clientes, RepositorioDeudas deudas,
                          RepositorioTurnos turnos, Reloj reloj) {
        this.abonos = abonos;
        this.clientes = clientes;
        this.deudas = deudas;
        this.turnos = turnos;
        this.reloj = reloj;
    }

    /**
     * @param primeroA la venta que el cliente dijo que paga, o {@code null}: a lo más viejo
     */
    public Abono ejecutar(UUID llave, UUID clienteId, Dinero monto, FormaPago forma, String referencia, String nota,
                          UUID primeroA, Actor actor) {
        if (actor == null) {
            throw new ReglaDeNegocioException("Al abono le falta quién lo recibe");
        }
        Optional<Abono> yaRecibido = llave == null ? Optional.empty() : abonos.buscarPorLlave(llave);
        if (yaRecibido.isPresent()) {
            return yaRecibido.get();
        }
        TurnoCaja turno = turnos.abiertoParaMover().orElse(null);
        if (turno != null && forma == FormaPago.EFECTIVO) {
            // El efectivo entra a un cajón concreto: tiene que ser el de quien lo recibe.
            turno.exigirQuePuedaOperar(actor);
        } else if (turno != null && !turno.puedeOperar(actor)) {
            // Una transferencia no toca el cajón: si el turno abierto es de otro cajero, el abono se recibe igual
            // y no se le cuelga a ese turno.
            turno = null;
        }
        Cliente cliente = clientes.buscarParaModificar(clienteId)
                .orElseThrow(() -> new ReglaDeNegocioException("Ese cliente no existe"));
        yaRecibido = llave == null ? Optional.empty() : abonos.buscarPorLlave(llave);
        if (yaRecibido.isPresent()) {
            return yaRecibido.get();
        }

        Instant ahora = reloj.ahora();
        Abono abono = Abono.recibir(abonos.siguienteNumero(), cliente.getId(), monto, forma, referencia, nota,
                turno == null ? null : turno.getId(), actor.id(), llave, ahora);
        CarteraDelCliente cartera = new CarteraDelCliente(cliente, deudas.delCliente(cliente.getId()),
                abonos.delCliente(cliente.getId()));
        cartera.abonar(abono, primeroA, ahora);

        // Las deudas primero: una aplicación no puede apuntar a una deuda que la base todavía no tiene.
        cartera.deudas().forEach(deudas::guardar);
        return abonos.guardar(abono);
    }
}
