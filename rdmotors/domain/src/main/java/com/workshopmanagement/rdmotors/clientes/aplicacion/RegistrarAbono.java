package com.workshopmanagement.rdmotors.clientes.aplicacion;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
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
 *   <li>Se reparte: a lo más viejo, o primero a lo que el cliente diga que paga (una venta, o los productos que se
 *       marquen, spec 0016).</li>
 * </ol>
 *
 * <p><b>Mixto</b> (spec 0017): parte en efectivo y parte por transferencia, escrito sobre el total. Son dos abonos que
 * nacen juntos, con el mismo pago: el efectivo entra al cajón y la transferencia no, cada uno como siempre.
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
     * @param primero lo que el cliente dijo que paga, en ese orden: productos o ventas (spec 0016); vacía o nula: a lo
     *                más viejo
     */
    public Abono ejecutar(UUID llave, UUID clienteId, Dinero monto, FormaPago forma, String referencia, String nota,
                          List<UUID> primero, Actor actor) {
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
        cartera.abonar(abono, primero == null ? List.of() : primero, ahora);

        // Las deudas primero: una aplicación no puede apuntar a una deuda que la base todavía no tiene.
        cartera.deudas().forEach(deudas::guardar);
        return abonos.guardar(abono);
    }

    /**
     * Un abono mixto (spec 0017): del total, {@code efectivo} entra al cajón y lo demás llegó por transferencia. Se
     * reparte como un solo pago, primero con el efectivo. El efectivo manda: hace falta el turno abierto de quien recibe,
     * y las dos partes quedan en ese turno.
     *
     * @param referencia la de la transferencia, si se escribió
     * @return las dos partes: primero la de efectivo, después la de transferencia
     */
    public List<Abono> ejecutarMixto(UUID llave, UUID clienteId, Dinero total, Dinero efectivo, String referencia,
                                     String nota, List<UUID> primero, Actor actor) {
        if (actor == null) {
            throw new ReglaDeNegocioException("Al abono le falta quién lo recibe");
        }
        Abono.PartesDelMixto partes = Abono.partesDelMixto(total, efectivo);
        Optional<List<Abono>> yaRecibido = elPagoDeLaLlave(llave);
        if (yaRecibido.isPresent()) {
            return yaRecibido.get();
        }
        TurnoCaja turno = turnos.abiertoParaMover().orElse(null);
        if (turno != null) {
            turno.exigirQuePuedaOperar(actor);
        }
        Cliente cliente = clientes.buscarParaModificar(clienteId)
                .orElseThrow(() -> new ReglaDeNegocioException("Ese cliente no existe"));
        yaRecibido = elPagoDeLaLlave(llave);
        if (yaRecibido.isPresent()) {
            return yaRecibido.get();
        }

        Instant ahora = reloj.ahora();
        UUID pagoId = UUID.randomUUID();
        UUID turnoId = turno == null ? null : turno.getId();
        Abono enEfectivo = Abono.recibir(abonos.siguienteNumero(), cliente.getId(), partes.efectivo(),
                FormaPago.EFECTIVO, null, nota, turnoId, actor.id(), llave, ahora, pagoId);
        Abono porTransferencia = Abono.recibir(abonos.siguienteNumero(), cliente.getId(), partes.transferencia(),
                FormaPago.TRANSFERENCIA, referencia, nota, turnoId, actor.id(),
                llave == null ? null : Abono.llaveDeLaTransferencia(llave), ahora, pagoId);
        CarteraDelCliente cartera = new CarteraDelCliente(cliente, deudas.delCliente(cliente.getId()),
                abonos.delCliente(cliente.getId()));
        cartera.abonar(List.of(enEfectivo, porTransferencia), primero == null ? List.of() : primero, ahora);

        cartera.deudas().forEach(deudas::guardar);
        return List.of(abonos.guardar(enEfectivo), abonos.guardar(porTransferencia));
    }

    /** Lo que ya se recibió con esa llave: el abono, o las partes de su pago si fue mixto. */
    private Optional<List<Abono>> elPagoDeLaLlave(UUID llave) {
        if (llave == null) {
            return Optional.empty();
        }
        return abonos.buscarPorLlave(llave).map(abono -> abono.esParteDeUnPago()
                ? abonos.delCliente(abono.getClienteId()).stream()
                        .filter(a -> abono.getPagoId().equals(a.getPagoId()))
                        .sorted(Comparator.comparingLong(Abono::getNumero))
                        .toList()
                : List.of(abono));
    }
}
