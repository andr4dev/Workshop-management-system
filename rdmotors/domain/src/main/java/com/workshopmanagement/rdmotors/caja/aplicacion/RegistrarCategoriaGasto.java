package com.workshopmanagement.rdmotors.caja.aplicacion;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.caja.dominio.CategoriaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.NaturalezaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioCategoriasGasto;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;

/**
 * CASO DE USO — crear una categoría de gasto (spec 0006, RF-002a): *"Publicidad"*, como gasto.
 *
 * <p><b>No hay dos con el mismo nombre</b>, sin distinguir mayúsculas ni tildes: "Papeleria" y "Papelería"
 * partirían el reporte en dos renglones. La base lo exige también, con un índice único.
 */
@Transactional
public class RegistrarCategoriaGasto {

    private final RepositorioCategoriasGasto categorias;

    public RegistrarCategoriaGasto(RepositorioCategoriasGasto categorias) {
        this.categorias = categorias;
    }

    public CategoriaGasto ejecutar(String nombre, NaturalezaGasto naturaleza, Actor actor) {
        return ejecutar(nombre, naturaleza, false, actor);
    }

    /** @param mensual si sus gastos suelen ser de todo el mes: el registro los sugiere así (spec 0007) */
    public CategoriaGasto ejecutar(String nombre, NaturalezaGasto naturaleza, boolean mensual, Actor actor) {
        actor.exigirAdministrador();
        CategoriaGasto nueva = CategoriaGasto.nueva(nombre, naturaleza, mensual);
        categorias.buscarPorNombre(nueva.getNombre()).ifPresent(existente -> {
            throw yaExiste(existente);
        });
        return categorias.guardar(nueva);
    }

    static ReglaDeNegocioException yaExiste(CategoriaGasto existente) {
        return new ReglaDeNegocioException("Ya existe la categoría «" + existente.getNombre() + "»"
                + (existente.isActiva() ? "" : ", desactivada"));
    }
}
