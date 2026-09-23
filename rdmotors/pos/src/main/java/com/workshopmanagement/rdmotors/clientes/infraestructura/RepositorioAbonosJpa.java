package com.workshopmanagement.rdmotors.clientes.infraestructura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.caja.dominio.MovimientoRepetidoException;
import com.workshopmanagement.rdmotors.clientes.dominio.Abono;
import com.workshopmanagement.rdmotors.clientes.dominio.puerto.RepositorioAbonos;

import lombok.RequiredArgsConstructor;

/** ADAPTADOR — los abonos sobre Postgres (spec 0008). */
@Repository
@RequiredArgsConstructor
class RepositorioAbonosJpa implements RepositorioAbonos {

    private static final String INDICE_LLAVE = "ux_abono_llave";

    private final AbonosSpringData jpa;
    private final JdbcTemplate jdbc;

    /** Como el de las ventas: la fila del contador queda tomada hasta el commit y vuelve si el abono se deshace (V20). */
    @Override
    public long siguienteNumero() {
        Long numero = jdbc.queryForObject(
                "update consecutivo set ultimo = ultimo + 1 where nombre = 'ABONO' returning ultimo", Long.class);
        if (numero == null) {
            throw new IllegalStateException("Falta la fila del consecutivo de abonos (V20)");
        }
        return numero;
    }

    @Override
    public List<Abono> delCliente(UUID clienteId) {
        return jpa.delCliente(clienteId);
    }

    @Override
    public Optional<Abono> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Abono> buscarPorLlave(UUID llave) {
        return jpa.findByLlaveIdempotencia(llave);
    }

    @Override
    public List<Abono> delTurno(UUID turnoId) {
        return jpa.findByTurnoIdOrderByNumeroAsc(turnoId);
    }

    /** Con {@code saveAndFlush} para que el choque con la llave única se traduzca aquí, como un gasto o un retiro. */
    @Override
    public Abono guardar(Abono abono) {
        try {
            return jpa.saveAndFlush(abono);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(INDICE_LLAVE)) {
                throw new MovimientoRepetidoException(abono.getLlaveIdempotencia());
            }
            throw e;
        }
    }
}

interface AbonosSpringData extends JpaRepository<Abono, UUID> {

    Optional<Abono> findByLlaveIdempotencia(UUID llave);

    List<Abono> findByTurnoIdOrderByNumeroAsc(UUID turnoId);

    /** Con sus aplicaciones de una vez: la cartera las recorre todas. */
    @Query("select distinct a from Abono a left join fetch a.aplicaciones where a.clienteId = :cliente order by a.numero")
    List<Abono> delCliente(@Param("cliente") UUID clienteId);
}
