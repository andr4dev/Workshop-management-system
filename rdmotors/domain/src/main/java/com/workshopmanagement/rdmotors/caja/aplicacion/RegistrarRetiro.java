package com.workshopmanagement.rdmotors.caja.aplicacion;

import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.Retiro;
import com.workshopmanagement.rdmotors.caja.dominio.SinTurnoAbiertoException;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioRetiros;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;

/**
 * CASO DE USO — registrar un retiro: el dueño se lleva plata del cajón (spec 0006, H2).
 *
 * <p>Mismo orden que un gasto del cajón: la llave, el turno bloqueado, la llave otra vez, y la confirmación
 * si es más de lo que debería haber (decisión 3).
 */
@Transactional
public class RegistrarRetiro {

    private final RepositorioRetiros retiros;
    private final RepositorioTurnos turnos;
    private final CalcularArqueo arqueo;
    private final Reloj reloj;

    public RegistrarRetiro(RepositorioRetiros retiros, RepositorioTurnos turnos, CalcularArqueo arqueo, Reloj reloj) {
        this.retiros = retiros;
        this.turnos = turnos;
        this.arqueo = arqueo;
        this.reloj = reloj;
    }

    /** @param confirmado el cajero ya confirmó que sí es más de lo que debería haber */
    public Retiro ejecutar(UUID llave, Dinero monto, String motivo, boolean confirmado, Actor actor) {
        if (actor == null) {
            throw new ReglaDeNegocioException("Al retiro le falta quién lo registra");
        }
        Optional<Retiro> yaRegistrado = llave == null ? Optional.empty() : retiros.buscarPorLlave(llave);
        if (yaRegistrado.isPresent()) {
            return yaRegistrado.get();
        }
        TurnoCaja turno = turnos.abiertoParaMover().orElseThrow(() -> new SinTurnoAbiertoException(
                "No hay un turno abierto. Ábrelo en Vender para registrar un retiro."));
        turno.exigirQuePuedaOperar(actor);
        yaRegistrado = llave == null ? Optional.empty() : retiros.buscarPorLlave(llave);
        if (yaRegistrado.isPresent()) {
            return yaRegistrado.get();
        }

        Retiro retiro = Retiro.registrar(turno.getId(), monto, motivo, actor.id(), llave, reloj.ahora());
        arqueo.exigirConfirmacionSiSupera(turno, retiro.getMonto(), confirmado);
        return retiros.guardar(retiro);
    }
}
