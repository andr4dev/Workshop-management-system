package com.workshopmanagement.rdmotors.compras.aplicacion;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCuentas;

/**
 * CASO DE USO — dar de baja una cuenta que ya no se usa (spec 0002, RF-004).
 *
 * <p>Se desactiva, no se borra: las compras que la usaron la siguen nombrando, y el historial
 * filtrado por esa cuenta tiene que seguir funcionando.
 *
 * <p>Desactivar una cuenta ya desactivada no es un error: el resultado es el mismo, y repetirlo
 * (un doble clic, un reintento) no debe asustar a nadie.
 */
@Transactional
public class DesactivarCuenta {

    private final RepositorioCuentas cuentas;

    public DesactivarCuenta(RepositorioCuentas cuentas) {
        this.cuentas = cuentas;
    }

    public CuentaPago ejecutar(UUID cuentaId, Actor actor) {
        actor.exigirAdministrador();
        CuentaPago cuenta = cuentas.buscar(cuentaId)
                .orElseThrow(() -> new ReglaDeNegocioException("La cuenta no existe"));
        cuenta.desactivar();
        return cuentas.guardar(cuenta);
    }
}
