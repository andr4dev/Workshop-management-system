package com.workshopmanagement.rdmotors.caja.aplicacion;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.CategoriaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioCategoriasGasto;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * CASO DE USO — dejar de usar una categoría de gasto (spec 0006, RF-002a).
 *
 * <p>Se desactiva, no se borra: los gastos que la usaron la siguen nombrando, y el reporte de meses pasados
 * tiene que seguir cuadrando. Desactivar una ya desactivada no es un error.
 */
@Transactional
public class DesactivarCategoriaGasto {

    private final RepositorioCategoriasGasto categorias;

    public DesactivarCategoriaGasto(RepositorioCategoriasGasto categorias) {
        this.categorias = categorias;
    }

    public CategoriaGasto ejecutar(UUID categoriaId, Actor actor) {
        actor.exigirAdministrador();
        CategoriaGasto categoria = categorias.buscar(categoriaId)
                .orElseThrow(() -> new ReglaDeNegocioException("La categoría no existe"));
        categoria.desactivar();
        return categorias.guardar(categoria);
    }
}
