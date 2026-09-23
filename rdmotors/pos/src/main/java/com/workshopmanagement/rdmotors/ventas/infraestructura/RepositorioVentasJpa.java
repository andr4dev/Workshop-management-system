package com.workshopmanagement.rdmotors.ventas.infraestructura;

import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;
import com.workshopmanagement.rdmotors.ventas.dominio.Venta;
import com.workshopmanagement.rdmotors.ventas.dominio.VentaRepetidaException;
import com.workshopmanagement.rdmotors.ventas.dominio.puerto.RepositorioVentas;

import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

/** ADAPTADOR — ventas sobre Postgres. */
@Repository
@RequiredArgsConstructor
class RepositorioVentasJpa implements RepositorioVentas {

    private static final String INDICE_LLAVE = "ux_venta_llave";

    private final VentasSpringData jpa;
    private final JdbcTemplate jdbc;

    /**
     * Una sola sentencia: sube el contador y devuelve el valor nuevo. La fila queda bloqueada hasta
     * el commit, así que ningún otro cobro puede pedir número mientras este no termine; y si este se
     * deshace, el número vuelve (V9).
     *
     * <p>Con {@code JdbcTemplate} y no con JPA: es un {@code UPDATE ... RETURNING}, que JPA no sabe
     * leer. Corre dentro de la misma transacción del cobro, como la auditoría.
     */
    @Override
    public long siguienteNumero() {
        Long numero = jdbc.queryForObject(
                "update consecutivo set ultimo = ultimo + 1 where nombre = 'VENTA' returning ultimo", Long.class);
        if (numero == null) {
            throw new IllegalStateException("Falta la fila del consecutivo de ventas (V9)");
        }
        return numero;
    }

    /**
     * Con {@code saveAndFlush} para que el choque con la llave única se traduzca aquí, igual que el
     * turno abierto. Solo pasa si dos cobros con la misma llave llegan a la vez.
     */
    @Override
    public Venta guardar(Venta venta) {
        try {
            return jpa.saveAndFlush(venta);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(INDICE_LLAVE)) {
                throw new VentaRepetidaException(venta.getLlaveIdempotencia());
            }
            throw e;
        }
    }

    @Override
    public Optional<Venta> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Venta> buscarPorLlave(UUID llave) {
        return jpa.findByLlaveIdempotencia(llave);
    }

    @Override
    public Optional<Venta> buscarPorNumero(long numero) {
        return jpa.findByNumero(numero);
    }

    @Override
    public Optional<Venta> buscarParaModificar(UUID id) {
        return jpa.bloquearPorId(id);
    }

    @Override
    public List<Venta> delTurno(UUID turnoId) {
        return jpa.findByTurnoIdOrderByNumeroDesc(turnoId);
    }

    @Override
    public Pagina<Venta> delCliente(UUID clienteId, int pagina, int tamano) {
        Page<Venta> pagina1 = jpa.findByClienteIdOrderByNumeroDesc(clienteId, PageRequest.of(pagina, tamano));
        return new Pagina<>(pagina1.getContent(), pagina1.getTotalElements(), pagina, tamano);
    }

    @Override
    public List<Venta> anuladasEnTurno(UUID turnoId) {
        return jpa.findByAnuladaEnTurnoIdOrderByNumeroDesc(turnoId);
    }
}

interface VentasSpringData extends JpaRepository<Venta, UUID> {

    Optional<Venta> findByLlaveIdempotencia(UUID llave);

    Optional<Venta> findByNumero(long numero);

    List<Venta> findByTurnoIdOrderByNumeroDesc(UUID turnoId);

    Page<Venta> findByClienteIdOrderByNumeroDesc(UUID clienteId, Pageable pagina);

    List<Venta> findByAnuladaEnTurnoIdOrderByNumeroDesc(UUID turnoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venta v where v.id = :id")
    Optional<Venta> bloquearPorId(@Param("id") UUID id);
}
