package com.workshopmanagement.rdmotors.caja.dominio.puerto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.workshopmanagement.rdmotors.caja.dominio.CategoriaGasto;

/** PUERTO — las categorías de gasto. */
public interface RepositorioCategoriasGasto {

    Optional<CategoriaGasto> buscar(UUID id);

    /** Sin distinguir mayúsculas ni tildes: "papeleria" encuentra a "Papelería". */
    Optional<CategoriaGasto> buscarPorNombre(String nombre);

    /** Activas y desactivadas, por nombre: los gastos viejos siguen nombrando a las desactivadas. */
    List<CategoriaGasto> todas();

    /**
     * Si la base encuentra otra con el mismo nombre (dos altas a la vez), lanza
     * {@link com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException}.
     */
    CategoriaGasto guardar(CategoriaGasto categoria);
}
