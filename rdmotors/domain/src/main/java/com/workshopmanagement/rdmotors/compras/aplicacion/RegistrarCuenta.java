package com.workshopmanagement.rdmotors.compras.aplicacion;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCuentas;

/**
 * CASO DE USO — dar de alta una cuenta desde la que se transfiere (spec 0002, RF-003).
 *
 * <p>Se invoca desde la pantalla de compra, igual que el proveedor: el administrador está pagando
 * por Nequi por primera vez y no debería perder lo capturado para crear la cuenta.
 *
 * <p><b>No se aceptan dos cuentas con el mismo nombre</b>, sin importar mayúsculas ni espacios de
 * más. Es la razón de que las cuentas sean una lista: si "nequi del dueño" y "Nequi del dueño"
 * pudieran convivir, el total por cuenta se partiría en dos. La base lo exige también, con un
 * índice único.
 */
@Transactional
public class RegistrarCuenta {

    private final RepositorioCuentas cuentas;

    public RegistrarCuenta(RepositorioCuentas cuentas) {
        this.cuentas = cuentas;
    }

    public CuentaPago ejecutar(String nombre, Actor actor) {
        actor.exigirAdministrador();
        CuentaPago nueva = CuentaPago.nueva(nombre);
        cuentas.buscarPorNombre(nueva.getNombre()).ifPresent(existente -> {
            throw new ReglaDeNegocioException("Ya existe la cuenta «" + existente.getNombre()
                    + "». Elígela en la lista.");
        });
        return cuentas.guardar(nueva);
    }
}
