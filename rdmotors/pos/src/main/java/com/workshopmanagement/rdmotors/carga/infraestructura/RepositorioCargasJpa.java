package com.workshopmanagement.rdmotors.carga.infraestructura;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.Hibernate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.carga.dominio.CargaDeInventario;
import com.workshopmanagement.rdmotors.carga.dominio.EstadoCarga;
import com.workshopmanagement.rdmotors.carga.dominio.ResumenCarga;
import com.workshopmanagement.rdmotors.carga.dominio.puerto.RepositorioCargas;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

/** ADAPTADOR — las cargas en revisión, sobre Postgres. */
@Repository
@RequiredArgsConstructor
class RepositorioCargasJpa implements RepositorioCargas {

    private static final String INDICE_FACTURA_EN_BORRADOR = "ux_carga_factura_en_borrador";

    private final CargasSpringData jpa;

    @Override
    public Optional<CargaDeInventario> buscar(UUID id) {
        return jpa.conRenglones(id);
    }

    /**
     * Dos consultas y no una: Postgres no deja poner {@code FOR UPDATE} del lado opcional de un {@code left join}.
     * Se bloquea la carga, y sus renglones se traen enseguida, ya con la carga bloqueada.
     */
    @Override
    public Optional<CargaDeInventario> buscarParaModificar(UUID id) {
        Optional<CargaDeInventario> carga = jpa.bloquear(id);
        carga.ifPresent(c -> Hibernate.initialize(c.getRenglones()));
        return carga;
    }

    @Override
    public Optional<CargaDeInventario> deLaFactura(String nitProveedor, String numeroFactura) {
        return jpa.deLaFactura(nitProveedor, numeroFactura, EstadoCarga.DESCARTADA, EstadoCarga.BORRADOR,
                Limit.of(1)).stream().findFirst();
    }

    @Override
    public List<ResumenCarga> recientes(int cerradasQueSeMuestran) {
        List<ResumenCarga> lista = new ArrayList<>(jpa.enBorrador(EstadoCarga.BORRADOR));
        lista.addAll(jpa.cerradas(EstadoCarga.BORRADOR, Limit.of(cerradasQueSeMuestran)));
        return lista;
    }

    /**
     * Con {@code saveAndFlush}, para traducir aquí dos subidas de la misma factura en el mismo instante: el caso de
     * uso ya lo avisa antes, pero entre su pregunta y este guardar cabe la otra.
     */
    @Override
    public CargaDeInventario guardar(CargaDeInventario carga) {
        try {
            return jpa.saveAndFlush(carga);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(INDICE_FACTURA_EN_BORRADOR)) {
                throw new ReglaDeNegocioException("Ya hay una pre-carga de la factura " + carga.getNumeroFactura()
                        + " sin terminar: ábrela en Cargas.");
            }
            throw e;
        }
    }
}

interface CargasSpringData extends JpaRepository<CargaDeInventario, UUID> {

    @Query("select c from CargaDeInventario c left join fetch c.renglones where c.id = :id")
    Optional<CargaDeInventario> conRenglones(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CargaDeInventario c where c.id = :id")
    Optional<CargaDeInventario> bloquear(@Param("id") UUID id);

    /** La que está en borrador primero; si no hay, la última confirmada. */
    @Query("""
            select c from CargaDeInventario c
            where c.nitProveedor = :nit and c.numeroFactura = :numero and c.estado <> :descartada
            order by case when c.estado = :borrador then 0 else 1 end, c.creadaEn desc
            """)
    List<CargaDeInventario> deLaFactura(@Param("nit") String nit, @Param("numero") String numero,
                                        @Param("descartada") EstadoCarga descartada,
                                        @Param("borrador") EstadoCarga borrador, Limit limite);

    String RESUMEN = """
            select new com.workshopmanagement.rdmotors.carga.dominio.ResumenCarga(
                c.id, c.estado, c.origen, c.nombreArchivo, c.numeroFactura, c.fechaFactura, c.proveedorId,
                (select count(r) from RenglonDeCarga r where r.carga = c),
                c.creadaEn, c.modificadaEn, c.cerradaEn, c.compraId)
            from CargaDeInventario c
            """;

    @Query(RESUMEN + " where c.estado = :borrador order by c.modificadaEn desc")
    List<ResumenCarga> enBorrador(@Param("borrador") EstadoCarga borrador);

    @Query(RESUMEN + " where c.estado <> :borrador order by c.cerradaEn desc")
    List<ResumenCarga> cerradas(@Param("borrador") EstadoCarga borrador, Limit limite);
}
