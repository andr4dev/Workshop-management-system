package com.workshopmanagement.rdmotors.compartido.infraestructura;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.compartido.dominio.DatosTienda;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioDatosTienda;

import lombok.RequiredArgsConstructor;

/** ADAPTADOR — los datos de la tienda sobre Postgres: la fila que siembra V10. */
@Repository
@RequiredArgsConstructor
class RepositorioDatosTiendaJpa implements RepositorioDatosTienda {

    private final DatosTiendaSpringData jpa;

    @Override
    public DatosTienda actuales() {
        // Si falta, la base no está migrada: no es un caso que la pantalla deba manejar.
        return jpa.findById(DatosTienda.ID)
                .orElseThrow(() -> new IllegalStateException("Falta la fila de datos_tienda que siembra V10"));
    }

    @Override
    public DatosTienda guardar(DatosTienda datos) {
        return jpa.save(datos);
    }
}

interface DatosTiendaSpringData extends JpaRepository<DatosTienda, Short> {
}
