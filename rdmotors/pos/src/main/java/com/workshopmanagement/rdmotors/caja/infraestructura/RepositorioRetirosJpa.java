package com.workshopmanagement.rdmotors.caja.infraestructura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.caja.dominio.MovimientoRepetidoException;
import com.workshopmanagement.rdmotors.caja.dominio.Retiro;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioRetiros;

import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

/** ADAPTADOR — retiros sobre Postgres. */
@Repository
@RequiredArgsConstructor
class RepositorioRetirosJpa implements RepositorioRetiros {

    private static final String INDICE_LLAVE = "ux_retiro_llave";

    private final RetirosSpringData jpa;

    @Override
    public Retiro guardar(Retiro retiro) {
        try {
            return jpa.saveAndFlush(retiro);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(INDICE_LLAVE)) {
                throw new MovimientoRepetidoException(retiro.getLlaveIdempotencia());
            }
            throw e;
        }
    }

    @Override
    public Optional<Retiro> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Retiro> buscarPorLlave(UUID llave) {
        return jpa.findByLlaveIdempotencia(llave);
    }

    @Override
    public Optional<Retiro> buscarParaModificar(UUID id) {
        return jpa.bloquearPorId(id);
    }

    @Override
    public List<Retiro> delTurno(UUID turnoId) {
        return jpa.findByTurnoIdOrderByRegistradoEnAscIdAsc(turnoId);
    }
}

interface RetirosSpringData extends JpaRepository<Retiro, UUID> {

    Optional<Retiro> findByLlaveIdempotencia(UUID llave);

    List<Retiro> findByTurnoIdOrderByRegistradoEnAscIdAsc(UUID turnoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Retiro r where r.id = :id")
    Optional<Retiro> bloquearPorId(@Param("id") UUID id);
}
