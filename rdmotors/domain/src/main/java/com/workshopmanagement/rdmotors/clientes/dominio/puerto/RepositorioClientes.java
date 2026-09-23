package com.workshopmanagement.rdmotors.clientes.dominio.puerto;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;

/** PUERTO — los clientes de la tienda (spec 0008). */
public interface RepositorioClientes {

    Optional<Cliente> buscar(UUID id);

    /**
     * Con intención de cambiar lo que debe (fiar, abonar, anular): el adaptador toma bloqueo de fila. Es el candado que
     * serializa todo lo que toca la cartera de ese cliente (plan 0008, decisión 4).
     */
    Optional<Cliente> buscarParaModificar(UUID id);

    /** Por la cédula o NIT como se escriba: se compara sin puntos, guiones ni espacios. */
    Optional<Cliente> buscarPorDocumento(String documento);

    /**
     * Los que coinciden por nombre (sin mayúsculas ni tildes), cédula o celular (sin puntos, guiones ni espacios),
     * por nombre, a lo sumo {@code limite}.
     */
    List<Cliente> buscarPorTexto(String texto, int limite);

    /** Los de esos ids: para mostrar a nombre de quién están varias ventas con una sola consulta. */
    List<Cliente> deIds(Collection<UUID> ids);

    /**
     * Con {@code saveAndFlush}: si otro cliente ya tiene esa cédula (dos altas a la vez), lanza
     * {@link com.workshopmanagement.rdmotors.clientes.dominio.ClienteRepetidoException} con el que quedó.
     */
    Cliente guardar(Cliente cliente);
}
