package com.workshopmanagement.rdmotors.correo.infraestructura;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.correo.dominio.Correo;
import com.workshopmanagement.rdmotors.correo.dominio.puerto.RepositorioCorreos;

import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

/** ADAPTADOR — la cola de correos sobre Postgres (spec 0010). */
@Repository
@RequiredArgsConstructor
class RepositorioCorreosJpa implements RepositorioCorreos {

    private final CorreosSpringData jpa;

    @Override
    public Correo guardar(Correo correo) {
        return jpa.save(correo);
    }

    /**
     * {@code FOR UPDATE SKIP LOCKED}: los que otra vuelta ya tomó, esta los salta en vez de esperarlos. Así dos
     * vueltas a la vez nunca se quedan con el mismo correo, y ninguna se traba esperando a la otra.
     */
    @Override
    public List<Correo> porMandar(Instant ahora, int cuantos) {
        return jpa.porMandar(ahora, cuantos);
    }

    @Override
    public List<Correo> ultimos(int cuantos) {
        return jpa.findAllByOrderByCreadoEnDesc(PageRequest.of(0, cuantos));
    }

    @Override
    public Optional<Correo> buscarParaModificar(UUID id) {
        return jpa.bloquearPorId(id);
    }
}

interface CorreosSpringData extends JpaRepository<Correo, UUID> {

    @Query(value = """
            select * from correo
            where estado = 'POR_MANDAR' and no_antes_de <= :ahora
            order by creado_en
            limit :cuantos
            for update skip locked
            """, nativeQuery = true)
    List<Correo> porMandar(@Param("ahora") Instant ahora, @Param("cuantos") int cuantos);

    List<Correo> findAllByOrderByCreadoEnDesc(org.springframework.data.domain.Pageable pagina);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Correo c where c.id = :id")
    Optional<Correo> bloquearPorId(@Param("id") UUID id);
}
