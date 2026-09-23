package com.workshopmanagement.rdmotors.inventario.infraestructura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.inventario.dominio.MovimientoKardex;
import com.workshopmanagement.rdmotors.inventario.dominio.TipoMovimiento;
import com.workshopmanagement.rdmotors.inventario.dominio.puerto.RepositorioKardex;

import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR — kardex sobre Postgres.
 *
 * <p>El puerto solo expone agregar y leer. Aunque {@code JpaRepository} traiga {@code delete} y
 * {@code save}, esos metodos no salen de aqui: el dominio nunca los ve, asi que el historial no se
 * puede corromper desde un caso de uso.
 */
@Repository
@RequiredArgsConstructor
class RepositorioKardexJpa implements RepositorioKardex {

    private final KardexSpringData jpa;

    @Override
    public void agregar(MovimientoKardex movimiento) {
        jpa.save(movimiento);
    }

    @Override
    public void agregarTodos(List<MovimientoKardex> movimientos) {
        jpa.saveAll(movimientos);
    }

    /** Por secuencia y no por fecha: una reversión y su entrada corregida comparten instante. */
    @Override
    public List<MovimientoKardex> historialDe(UUID varianteId) {
        return jpa.findByVarianteIdOrderBySecuenciaAsc(varianteId);
    }

    @Override
    public Optional<MovimientoKardex> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public boolean huboSalidasDespuesDe(UUID varianteId, long secuencia) {
        return jpa.contarSalidasDespuesDe(varianteId, secuencia,
                List.of(TipoMovimiento.CORRECCION_COMPRA, TipoMovimiento.ANULACION_COMPRA)) > 0;
    }

    @Override
    public Optional<MovimientoKardex> ultimoAntesDe(UUID varianteId, long secuencia) {
        return jpa.findFirstByVarianteIdAndSecuenciaLessThanOrderBySecuenciaDesc(varianteId, secuencia);
    }
}

interface KardexSpringData extends JpaRepository<MovimientoKardex, UUID> {

    List<MovimientoKardex> findByVarianteIdOrderBySecuenciaAsc(UUID varianteId);

    Optional<MovimientoKardex> findFirstByVarianteIdAndSecuenciaLessThanOrderBySecuenciaDesc(
            UUID varianteId, Long secuencia);

    /**
     * Salidas que consumen inventario. Las reversiones de compra devuelven, no consumen. Una salida con
     * su reversión al mismo promedio tampoco: la venta anulada se canceló al peso (RF-030). El índice
     * único {@code ux_kardex_revertido} hace que la subconsulta sea una búsqueda, no un recorrido.
     */
    @Query("""
            select count(m) from MovimientoKardex m
            where m.variante.id = :varianteId
              and m.secuencia > :secuencia
              and m.cantidadDelta < 0
              and m.tipo not in :reversiones
              and not exists (
                  select r from MovimientoKardex r
                  where r.movimientoRevertidoId = m.id
                    and (r.costoPromedioDespues = m.costoPromedioDespues
                         or (r.costoPromedioDespues is null and m.costoPromedioDespues is null)))
            """)
    long contarSalidasDespuesDe(@Param("varianteId") UUID varianteId,
                                @Param("secuencia") long secuencia,
                                @Param("reversiones") List<TipoMovimiento> reversiones);
}
