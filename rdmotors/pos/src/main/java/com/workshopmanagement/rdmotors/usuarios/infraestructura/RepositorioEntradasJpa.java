package com.workshopmanagement.rdmotors.usuarios.infraestructura;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;
import com.workshopmanagement.rdmotors.usuarios.dominio.Entrada;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioEntradas;

import lombok.RequiredArgsConstructor;

/** ADAPTADOR — el registro de entradas, sobre Postgres (spec 0004, RF-025). Solo se agrega. */
@Repository
@RequiredArgsConstructor
class RepositorioEntradasJpa implements RepositorioEntradas {

    private final EntradasSpringData jpa;

    @Override
    public void registrar(Entrada entrada) {
        jpa.save(entrada);
    }

    @Override
    public Pagina<Entrada> ultimas(int pagina, int tamano) {
        Page<Entrada> p = jpa.ultimas(PageRequest.of(pagina, tamano));
        return new Pagina<>(p.getContent(), p.getTotalElements(), pagina, tamano);
    }
}

interface EntradasSpringData extends JpaRepository<Entrada, UUID> {

    /** El id desempata: dos intentos en el mismo instante no cambian de página entre una petición y otra. */
    @Query(value = "select e from Entrada e order by e.momento desc, e.id",
            countQuery = "select count(e) from Entrada e")
    Page<Entrada> ultimas(org.springframework.data.domain.Pageable pagina);
}
