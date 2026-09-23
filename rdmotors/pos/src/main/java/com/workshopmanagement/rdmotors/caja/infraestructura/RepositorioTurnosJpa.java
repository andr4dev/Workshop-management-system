package com.workshopmanagement.rdmotors.caja.infraestructura;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.caja.dominio.EstadoTurno;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoCaja;
import com.workshopmanagement.rdmotors.caja.dominio.TurnoYaAbiertoException;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioTurnos;
import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;

import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

/** ADAPTADOR — turnos de caja sobre Postgres. */
@Repository
@RequiredArgsConstructor
class RepositorioTurnosJpa implements RepositorioTurnos {

    private static final String INDICE_TURNO_ABIERTO = "ux_turno_abierto";

    private final TurnosSpringData jpa;

    @Override
    public Optional<TurnoCaja> abierto() {
        return jpa.findByEstado(EstadoTurno.ABIERTO);
    }

    /**
     * {@code SELECT ... FOR SHARE}: los cobros, gastos y retiros se lo reparten, y un cierre (que pide
     * {@code FOR UPDATE}) los espera. Si un cierre ya lo tiene, esta consulta espera a que termine y, al
     * reevaluar la fila, el turno ya no está abierto: vuelve vacía. Es lo que hace que un cobro nunca quede
     * en un turno cerrado sin contar (spec 0006, RF-017).
     */
    @Override
    public Optional<TurnoCaja> abiertoParaMover() {
        return jpa.bloquearCompartido(EstadoTurno.ABIERTO);
    }

    @Override
    public Optional<TurnoCaja> buscarParaCerrar(UUID id) {
        return jpa.bloquearExclusivo(id);
    }

    @Override
    public Optional<TurnoCaja> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Pagina<TurnoCaja> cerrados(int pagina, int tamano) {
        Page<TurnoCaja> p = jpa.cerrados(EstadoTurno.CERRADO, PageRequest.of(pagina, tamano));
        return new Pagina<>(p.getContent(), p.getTotalElements(), pagina, tamano);
    }

    @Override
    public Pagina<TurnoCaja> cerradosDe(UUID abiertoPorId, int pagina, int tamano) {
        Page<TurnoCaja> p = jpa.cerradosDe(EstadoTurno.CERRADO, abiertoPorId, PageRequest.of(pagina, tamano));
        return new Pagina<>(p.getContent(), p.getTotalElements(), pagina, tamano);
    }

    /**
     * Con {@code saveAndFlush}: el insert tiene que llegar a la base aquí, dentro de este método, para
     * que el choque con el índice único se traduzca aquí. Con {@code save} llegaría al commit, fuera
     * de este adaptador, como un error genérico de la base.
     */
    @Override
    public TurnoCaja guardar(TurnoCaja turno) {
        try {
            return jpa.saveAndFlush(turno);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(INDICE_TURNO_ABIERTO)) {
                throw new TurnoYaAbiertoException(null, null);
            }
            throw e;
        }
    }
}

interface TurnosSpringData extends JpaRepository<TurnoCaja, UUID> {

    Optional<TurnoCaja> findByEstado(EstadoTurno estado);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select t from TurnoCaja t where t.estado = :estado")
    Optional<TurnoCaja> bloquearCompartido(@Param("estado") EstadoTurno estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TurnoCaja t where t.id = :id")
    Optional<TurnoCaja> bloquearExclusivo(@Param("id") UUID id);

    /** El id desempata: dos cierres en el mismo instante no cambian de página entre una petición y otra. */
    @Query(value = "select t from TurnoCaja t where t.estado = :estado order by t.cerradoEn desc, t.id",
            countQuery = "select count(t) from TurnoCaja t where t.estado = :estado")
    Page<TurnoCaja> cerrados(@Param("estado") EstadoTurno estado, Pageable pagina);

    @Query(value = "select t from TurnoCaja t where t.estado = :estado and t.abiertoPorId = :abiertoPor "
            + "order by t.cerradoEn desc, t.id",
            countQuery = "select count(t) from TurnoCaja t where t.estado = :estado and t.abiertoPorId = :abiertoPor")
    Page<TurnoCaja> cerradosDe(@Param("estado") EstadoTurno estado, @Param("abiertoPor") UUID abiertoPor,
                               Pageable pagina);
}
