package com.workshopmanagement.rdmotors.caja.aplicacion;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.CategoriaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioCategoriasGasto;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * CASO DE USO — renombrar una categoría de gasto y decir si se paga cada mes (spec 0006, RF-002a; spec 0007,
 * RF-008a). Sus gastos la siguen nombrando, con el nombre nuevo. La naturaleza no cambia: movería plata entre
 * costo y gasto en meses ya reportados. Que sea mensual tampoco cambia los gastos ya registrados.
 *
 * <p>Reemplaza los dos datos: repetirlo deja el mismo estado.
 */
@Transactional
public class ActualizarCategoriaGasto {

    private final RepositorioCategoriasGasto categorias;

    public ActualizarCategoriaGasto(RepositorioCategoriasGasto categorias) {
        this.categorias = categorias;
    }

    public CategoriaGasto ejecutar(UUID categoriaId, String nombre, boolean mensual, Actor actor) {
        actor.exigirAdministrador();
        CategoriaGasto categoria = categorias.buscar(categoriaId)
                .orElseThrow(() -> new ReglaDeNegocioException("La categoría no existe"));
        String limpio = CategoriaGasto.normalizar(nombre);
        categorias.buscarPorNombre(limpio)
                .filter(otra -> !otra.getId().equals(categoriaId))
                .ifPresent(otra -> {
                    throw RegistrarCategoriaGasto.yaExiste(otra);
                });
        categoria.renombrar(limpio);
        categoria.marcarMensual(mensual);
        return categorias.guardar(categoria);
    }
}
