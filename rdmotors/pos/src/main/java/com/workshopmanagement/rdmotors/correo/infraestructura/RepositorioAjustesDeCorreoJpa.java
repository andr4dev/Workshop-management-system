package com.workshopmanagement.rdmotors.correo.infraestructura;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.correo.dominio.AjustesDeCorreo;
import com.workshopmanagement.rdmotors.correo.dominio.puerto.RepositorioAjustesDeCorreo;

import lombok.RequiredArgsConstructor;

/** ADAPTADOR — a quién le llega el resumen del cierre (spec 0010). La fila la siembra V24. */
@Repository
@RequiredArgsConstructor
class RepositorioAjustesDeCorreoJpa implements RepositorioAjustesDeCorreo {

    private final AjustesDeCorreoSpringData jpa;

    @Override
    public AjustesDeCorreo obtener() {
        return jpa.findById(AjustesDeCorreo.ID).orElseGet(AjustesDeCorreo::iniciales);
    }

    @Override
    public AjustesDeCorreo guardar(AjustesDeCorreo ajustes) {
        return jpa.save(ajustes);
    }
}

interface AjustesDeCorreoSpringData extends JpaRepository<AjustesDeCorreo, Short> {
}
