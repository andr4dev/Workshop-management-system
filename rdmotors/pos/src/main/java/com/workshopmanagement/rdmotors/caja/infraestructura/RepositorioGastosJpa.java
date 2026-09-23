package com.workshopmanagement.rdmotors.caja.infraestructura;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.caja.dominio.FiltroGastos;
import com.workshopmanagement.rdmotors.caja.dominio.Gasto;
import com.workshopmanagement.rdmotors.caja.dominio.MovimientoRepetidoException;
import com.workshopmanagement.rdmotors.caja.dominio.TotalesGastos;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioGastos;
import com.workshopmanagement.rdmotors.compartido.dominio.Dinero;
import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;

/** ADAPTADOR — gastos sobre Postgres. */
@Repository
@RequiredArgsConstructor
class RepositorioGastosJpa implements RepositorioGastos {

    private static final String INDICE_LLAVE = "ux_gasto_llave";

    private final GastosSpringData jpa;

    @PersistenceContext
    private EntityManager em;

    /** Con {@code saveAndFlush} para que el choque con la llave única se traduzca aquí, como en la venta. */
    @Override
    public Gasto guardar(Gasto gasto) {
        try {
            return jpa.saveAndFlush(gasto);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(INDICE_LLAVE)) {
                throw new MovimientoRepetidoException(gasto.getLlaveIdempotencia());
            }
            throw e;
        }
    }

    @Override
    public Optional<Gasto> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Gasto> buscarPorLlave(UUID llave) {
        return jpa.findByLlaveIdempotencia(llave);
    }

    @Override
    public Optional<Gasto> buscarParaModificar(UUID id) {
        return jpa.bloquearPorId(id);
    }

    /**
     * Armada solo con los filtros presentes, como el historial de compras: un parámetro nulo en
     * {@code (:x is null or ...)} viaja con un tipo que Postgres no sabe comparar. El id desempata el orden.
     */
    @Override
    public Pagina<Gasto> listar(FiltroGastos filtro, int pagina, int tamano) {
        Condiciones condiciones = Condiciones.de(filtro);
        TypedQuery<Gasto> consulta = em.createQuery("""
                select g from Gasto g
                join fetch g.categoria
                left join fetch g.cuenta
                """ + condiciones.donde() + " order by g.fecha desc, g.registradoEn desc, g.id", Gasto.class);
        TypedQuery<Long> conteo = em.createQuery("select count(g) from Gasto g" + condiciones.donde(), Long.class);
        condiciones.aplicarA(consulta);
        condiciones.aplicarA(conteo);
        List<Gasto> elementos = consulta.setFirstResult(pagina * tamano).setMaxResults(tamano).getResultList();
        return new Pagina<>(elementos, conteo.getSingleResult(), pagina, tamano);
    }

    /**
     * El total y lo del cajón salen de sumas separadas: que {@code delCajon + porFuera} dé el total es algo
     * que se comprueba, no algo que pasa por construcción.
     */
    @Override
    public TotalesGastos totales(FiltroGastos filtro) {
        Condiciones condiciones = Condiciones.de(filtro).conVigentes();
        TypedQuery<Object[]> consulta = em.createQuery("""
                select coalesce(sum(g.monto.monto), 0), count(g),
                       coalesce(sum(case when g.delCajon = true then g.monto.monto else 0 end), 0),
                       coalesce(sum(case when g.delCajon = false then g.monto.monto else 0 end), 0)
                from Gasto g
                """ + condiciones.donde(), Object[].class);
        condiciones.aplicarA(consulta);
        Object[] fila = consulta.getSingleResult();
        return new TotalesGastos(pesos(fila[0]), (Long) fila[1], pesos(fila[2]), pesos(fila[3]));
    }

    @Override
    public List<Gasto> delCajonEnTurno(UUID turnoId) {
        return jpa.delCajonEnTurno(turnoId);
    }

    private static Dinero pesos(Object valor) {
        return Dinero.de(new BigDecimal(valor.toString()));
    }

    private record Condiciones(List<String> clausulas, Map<String, Object> parametros) {

        static Condiciones de(FiltroGastos filtro) {
            List<String> clausulas = new ArrayList<>();
            Map<String, Object> parametros = new LinkedHashMap<>();
            if (filtro.desde() != null) {
                clausulas.add("g.fecha >= :desde");
                parametros.put("desde", filtro.desde());
            }
            if (filtro.hasta() != null) {
                clausulas.add("g.fecha <= :hasta");
                parametros.put("hasta", filtro.hasta());
            }
            if (filtro.categoriaId() != null) {
                clausulas.add("g.categoria.id = :categoriaId");
                parametros.put("categoriaId", filtro.categoriaId());
            }
            return new Condiciones(clausulas, parametros);
        }

        /** Los totales no cuentan los anulados; la lista sí los muestra, tachados. */
        Condiciones conVigentes() {
            List<String> mas = new ArrayList<>(clausulas);
            mas.add("g.anuladoEn is null");
            return new Condiciones(mas, parametros);
        }

        String donde() {
            return clausulas.isEmpty() ? "" : " where " + String.join(" and ", clausulas);
        }

        void aplicarA(TypedQuery<?> consulta) {
            parametros.forEach(consulta::setParameter);
        }
    }
}

interface GastosSpringData extends JpaRepository<Gasto, UUID> {

    Optional<Gasto> findByLlaveIdempotencia(UUID llave);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Gasto g where g.id = :id")
    Optional<Gasto> bloquearPorId(@Param("id") UUID id);

    @Query("""
            select g from Gasto g
            join fetch g.categoria
            where g.delCajon = true and g.turnoId = :turnoId
            order by g.registradoEn, g.id
            """)
    List<Gasto> delCajonEnTurno(@Param("turnoId") UUID turnoId);
}
