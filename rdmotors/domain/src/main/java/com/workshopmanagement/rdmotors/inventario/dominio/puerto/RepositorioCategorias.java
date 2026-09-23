package com.workshopmanagement.rdmotors.inventario.dominio.puerto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.workshopmanagement.rdmotors.inventario.dominio.Categoria;

/**
 * PUERTO — las categorías por sistema del vehículo.
 *
 * <p>No hay {@code borrar}: una categoría con productos no se elimina, se desactiva. Borrarla
 * dejaría productos huérfanos y huecos en los reportes históricos. Que el puerto no ofrezca la
 * operación es la forma más barata de que nadie la invoque por descuido.
 */
public interface RepositorioCategorias {

    Optional<Categoria> buscar(UUID id);

    /** Ordenadas por su campo de orden, que el administrador puede cambiar. */
    List<Categoria> activas();

    Categoria guardar(Categoria categoria);
}
