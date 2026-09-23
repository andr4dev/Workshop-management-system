package com.workshopmanagement.rdmotors.clientes.infraestructura;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.clientes.dominio.Cliente;
import com.workshopmanagement.rdmotors.clientes.dominio.ClienteRepetidoException;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioClientes;
import com.workshopmanagement.rdmotors.compartido.dominio.TextoDeBusqueda;

import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

/** ADAPTADOR — los clientes sobre Postgres (spec 0008). */
@Repository
@RequiredArgsConstructor
class RepositorioClientesJpa implements RepositorioClientes {

    private static final String INDICE_DOCUMENTO = "ux_cliente_documento";

    private final ClientesSpringData jpa;

    @Override
    public Optional<Cliente> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Cliente> buscarParaModificar(UUID id) {
        return jpa.bloquearPorId(id);
    }

    @Override
    public Optional<Cliente> buscarPorDocumento(String documento) {
        String normalizado = Cliente.normalizarDocumento(documento);
        return normalizado == null ? Optional.empty() : jpa.findByDocumentoNormalizado(normalizado);
    }

    /**
     * El nombre se compara con su forma sin tildes guardada; la cédula y el celular, por sus caracteres. Lo escrito
     * se normaliza aquí con las mismas reglas que al guardar.
     */
    @Override
    public List<Cliente> buscarPorTexto(String texto, int limite) {
        String nombre = "%" + escapar(TextoDeBusqueda.normalizar(texto.trim())) + "%";
        String documento = Cliente.normalizarDocumento(texto);
        String celular = Cliente.normalizarCelular(texto);
        return jpa.buscar(nombre,
                documento == null ? null : "%" + escapar(documento) + "%",
                celular == null ? null : "%" + celular + "%",
                Limit.of(limite));
    }

    @Override
    public List<Cliente> deIds(Collection<UUID> ids) {
        return ids.isEmpty() ? List.of() : jpa.findAllById(ids);
    }

    /** Con {@code saveAndFlush} para traducir aquí el choque de dos altas con la misma cédula a la vez. */
    @Override
    public Cliente guardar(Cliente cliente) {
        try {
            return jpa.saveAndFlush(cliente);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(INDICE_DOCUMENTO)) {
                Cliente existente = jpa.findByDocumentoNormalizado(cliente.getDocumentoNormalizado()).orElseThrow(() -> e);
                throw new ClienteRepetidoException(existente.getId(), existente.getNombre());
            }
            throw e;
        }
    }

    /** {@code %} y {@code _} escritos por el cajero se buscan tal cual, no como comodines. */
    private static String escapar(String texto) {
        return texto.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}

interface ClientesSpringData extends JpaRepository<Cliente, UUID> {

    Optional<Cliente> findByDocumentoNormalizado(String documentoNormalizado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cliente c where c.id = :id")
    Optional<Cliente> bloquearPorId(@Param("id") UUID id);

    @Query("""
            select c from Cliente c
            where c.nombreNormalizado like :nombre escape '!'
               or (:documento is not null and c.documentoNormalizado like :documento escape '!')
               or (:celular is not null and c.celularNormalizado like :celular)
            order by c.nombreNormalizado, c.id
            """)
    List<Cliente> buscar(@Param("nombre") String nombre, @Param("documento") String documento,
                         @Param("celular") String celular, Limit limite);
}
