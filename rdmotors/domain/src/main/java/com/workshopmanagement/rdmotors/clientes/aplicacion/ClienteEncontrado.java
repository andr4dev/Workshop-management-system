package com.workshopmanagement.rdmotors.clientes.aplicacion;

import java.util.List;
import java.util.UUID;

import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * Un cliente como lo muestra el cobro al escogerlo (spec 0008, RF-003): sus datos, cuánto debe y qué le falta para
 * poder fiarle.
 *
 * @param datosQueFaltan "la cédula", "el celular": lo que conviene completar; no impide fiarle
 */
public record ClienteEncontrado(UUID id, String nombre, String documento, String celular, String direccion,
                                String nota, boolean fiadoCerrado, String motivoFiadoCerrado, Dinero debe,
                                List<String> datosQueFaltan) {

    static ClienteEncontrado de(Cliente c, Dinero debe) {
        return new ClienteEncontrado(c.getId(), c.getNombre(), c.getDocumento(), c.getCelular(), c.getDireccion(),
                c.getNota(), c.isFiadoCerrado(), c.getMotivoFiadoCerrado(), debe == null ? Dinero.CERO : debe,
                c.datosQueFaltan());
    }
}
