package com.workshopmanagement.rdmotors.compras.aplicacion;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compras.dominio.Proveedor;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioProveedores;

/**
 * CASO DE USO — dar de alta un proveedor.
 *
 * <p>Se puede invocar desde la pantalla de proveedores o <b>desde el registro de una compra</b>:
 * el administrador tiene la factura de un importador nuevo enfrente y no debería tener que
 * abandonar lo que está capturando para darlo de alta.
 *
 * <p>Solo el nombre es obligatorio. Exigir NIT y teléfono para registrar una compra que ya ocurrió
 * sería pedirle al administrador datos que quizá no tiene a mano, para bloquear algo que de todos
 * modos pasó.
 */
@Transactional
public class RegistrarProveedor {

    private final RepositorioProveedores proveedores;

    public RegistrarProveedor(RepositorioProveedores proveedores) {
        this.proveedores = proveedores;
    }

    public Proveedor ejecutar(String nombre, String nit, String telefono, Actor actor) {
        actor.exigirAdministrador();
        return proveedores.guardar(Proveedor.nuevo(nombre, nit, telefono));
    }
}
