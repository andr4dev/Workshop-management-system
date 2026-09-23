package com.workshopmanagement.rdmotors.respaldo.infraestructura;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.respaldo.dominio.Respaldo;
import com.workshopmanagement.rdmotors.respaldo.dominio.puerto.RepositorioRespaldos;

import lombok.RequiredArgsConstructor;

/** ADAPTADOR — el registro de respaldos sobre Postgres. Son pocas filas: una por día. */
@Repository
@RequiredArgsConstructor
class RepositorioRespaldosJpa implements RepositorioRespaldos {

    private final RespaldosSpringData jpa;

    @Override
    public Respaldo guardar(Respaldo respaldo) {
        return jpa.save(respaldo);
    }

    @Override
    public List<Respaldo> todos() {
        return jpa.findAllByOrderByHechoEnDesc();
    }
}

interface RespaldosSpringData extends JpaRepository<Respaldo, UUID> {

    List<Respaldo> findAllByOrderByHechoEnDesc();
}
