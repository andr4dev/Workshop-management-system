package com.workshopmanagement.rdmotors.clientes.aplicacion;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioClientes;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioDeudas;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

/**
 * CASO DE USO — buscar a quién fiarle, o a nombre de quién va una venta (spec 0008, RF-003): por nombre, cédula o
 * celular, sin tildes ni mayúsculas, con lo que debe cada uno.
 */
@Transactional(readOnly = true)
public class BuscarClientes {

    public static final int LIMITE = 10;

    private final RepositorioClientes clientes;
    private final RepositorioDeudas deudas;

    public BuscarClientes(RepositorioClientes clientes, RepositorioDeudas deudas) {
        this.clientes = clientes;
        this.deudas = deudas;
    }

    /** Sin texto no se busca: la lista de todos es la Cartera. */
    public List<ClienteEncontrado> porTexto(String texto) {
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.isEmpty()) {
            return List.of();
        }
        return conLoQueDeben(clientes.buscarPorTexto(limpio, LIMITE));
    }

    public Optional<ClienteEncontrado> porId(UUID id) {
        return clientes.buscar(id).map(c -> conLoQueDeben(List.of(c)).getFirst());
    }

    private List<ClienteEncontrado> conLoQueDeben(List<Cliente> encontrados) {
        Map<UUID, Dinero> debe = deudas.debeDe(encontrados.stream().map(Cliente::getId).toList());
        return encontrados.stream().map(c -> ClienteEncontrado.de(c, debe.get(c.getId()))).toList();
    }
}
