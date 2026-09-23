package com.workshopmanagement.rdmotors.clientes.infraestructura;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.clientes.dominio.Deuda;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioDeudas;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;

import lombok.RequiredArgsConstructor;

/** ADAPTADOR — las deudas de los clientes sobre Postgres (spec 0008). */
@Repository
@RequiredArgsConstructor
class RepositorioDeudasJpa implements RepositorioDeudas {

    private final DeudasSpringData jpa;

    @Override
    public List<Deuda> delCliente(UUID clienteId) {
        return jpa.findByClienteIdOrderByFechaAscRegistradaEnAscIdAsc(clienteId);
    }

    @Override
    public Optional<Deuda> deLaVenta(UUID ventaId) {
        return jpa.findByVentaId(ventaId);
    }

    @Override
    public List<Deuda> deLasVentas(Collection<UUID> ventaIds) {
        return ventaIds.isEmpty() ? List.of() : jpa.findByVentaIdIn(ventaIds);
    }

    /** La misma cuenta que {@code Deuda.pendiente()}: lo fiado menos lo abonado, sin las anuladas. */
    @Override
    public Map<UUID, Dinero> debeDe(Collection<UUID> clienteIds) {
        Map<UUID, Dinero> debe = new LinkedHashMap<>();
        if (clienteIds.isEmpty()) {
            return debe;
        }
        for (Object[] fila : jpa.pendientePorCliente(clienteIds)) {
            debe.put((UUID) fila[0], Dinero.de((BigDecimal) fila[1]));
        }
        return debe;
    }

    /** Con {@code saveAndFlush}: la deuda tiene que estar en la base antes de que un abono se le aplique. */
    @Override
    public Deuda guardar(Deuda deuda) {
        return jpa.saveAndFlush(deuda);
    }
}

interface DeudasSpringData extends JpaRepository<Deuda, UUID> {

    List<Deuda> findByClienteIdOrderByFechaAscRegistradaEnAscIdAsc(UUID clienteId);

    Optional<Deuda> findByVentaId(UUID ventaId);

    List<Deuda> findByVentaIdIn(Collection<UUID> ventaIds);

    @Query("""
            select d.clienteId, sum(d.monto.monto - d.abonado.monto) from Deuda d
            where d.clienteId in :clientes and d.anuladaEn is null and d.monto.monto > d.abonado.monto
            group by d.clienteId
            """)
    List<Object[]> pendientePorCliente(@Param("clientes") Collection<UUID> clientes);
}
