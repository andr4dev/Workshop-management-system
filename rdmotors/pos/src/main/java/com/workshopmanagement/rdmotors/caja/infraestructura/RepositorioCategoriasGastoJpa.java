package com.workshopmanagement.rdmotors.caja.infraestructura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.workshopmanagement.rdmotors.caja.dominio.CategoriaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioCategoriasGasto;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.TextoDeBusqueda;

import lombok.RequiredArgsConstructor;

/** ADAPTADOR — categorías de gasto sobre Postgres. */
@Repository
@RequiredArgsConstructor
class RepositorioCategoriasGastoJpa implements RepositorioCategoriasGasto {

    private static final String INDICE_NOMBRE = "ux_categoria_gasto_nombre";

    private final CategoriasGastoSpringData jpa;

    @Override
    public Optional<CategoriaGasto> buscar(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<CategoriaGasto> buscarPorNombre(String nombre) {
        return jpa.buscarPorNombre(TextoDeBusqueda.normalizar(CategoriaGasto.normalizar(nombre)));
    }

    @Override
    public List<CategoriaGasto> todas() {
        return jpa.findAllByOrderByNombreAsc();
    }

    /** Con {@code saveAndFlush} para traducir aquí el choque de dos altas con el mismo nombre a la vez. */
    @Override
    public CategoriaGasto guardar(CategoriaGasto categoria) {
        try {
            return jpa.saveAndFlush(categoria);
        } catch (DataIntegrityViolationException e) {
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(INDICE_NOMBRE)) {
                throw new ReglaDeNegocioException("Ya existe la categoría «" + categoria.getNombre() + "»");
            }
            throw e;
        }
    }
}

interface CategoriasGastoSpringData extends JpaRepository<CategoriaGasto, UUID> {

    /** Misma comparación que el índice único {@code ux_categoria_gasto_nombre}: sin mayúsculas ni tildes. */
    @Query("select c from CategoriaGasto c where sin_tildes(c.nombre) = :normalizado")
    Optional<CategoriaGasto> buscarPorNombre(@Param("normalizado") String normalizado);

    List<CategoriaGasto> findAllByOrderByNombreAsc();
}
