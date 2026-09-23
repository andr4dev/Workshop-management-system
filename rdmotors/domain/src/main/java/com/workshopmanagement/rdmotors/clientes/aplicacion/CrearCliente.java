package com.workshopmanagement.rdmotors.clientes.aplicacion;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.ClienteRepetidoException;
import com.workshopmanagement.rdmotors.clientes.dominio.DatosCliente;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioClientes;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;

/**
 * CASO DE USO — un cliente nuevo (spec 0008, RF-001 y RF-003). Lo crean el cajero y el administrador: en el mostrador,
 * al fiarle a alguien que no existe, sin salir de la venta.
 *
 * <p>Si la cédula ya es de alguien, no se crea otro: {@link ClienteRepetidoException} dice cuál, y la pantalla lo usa.
 */
@Transactional
public class CrearCliente {

    private final RepositorioClientes clientes;
    private final Reloj reloj;

    public CrearCliente(RepositorioClientes clientes, Reloj reloj) {
        this.clientes = clientes;
        this.reloj = reloj;
    }

    public Cliente ejecutar(DatosCliente datos, Actor actor) {
        if (actor == null) {
            throw new ReglaDeNegocioException("Falta quién crea el cliente");
        }
        Cliente nuevo = Cliente.nuevo(datos, actor.id(), reloj.ahora());
        if (nuevo.getDocumento() != null) {
            clientes.buscarPorDocumento(nuevo.getDocumento()).ifPresent(existente -> {
                throw new ClienteRepetidoException(existente.getId(), existente.getNombre());
            });
        }
        return clientes.guardar(nuevo);
    }
}
