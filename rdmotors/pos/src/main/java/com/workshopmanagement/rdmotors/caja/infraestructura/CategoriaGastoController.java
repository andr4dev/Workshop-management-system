package com.workshopmanagement.rdmotors.caja.infraestructura;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.workshopmanagement.rdmotors.caja.aplicacion.ActualizarCategoriaGasto;
import com.workshopmanagement.rdmotors.caja.aplicacion.DesactivarCategoriaGasto;
import com.workshopmanagement.rdmotors.caja.aplicacion.RegistrarCategoriaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.CategoriaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.NaturalezaGasto;
import com.workshopmanagement.rdmotors.caja.dominio.puerto.RepositorioCategoriasGasto;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.ActorActual;

import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — categorías de gasto (spec 0006, RF-002a).
 *
 * <p>Crear, renombrar y desactivar pasan por sus casos de uso (tienen la regla del nombre repetido); listar usa
 * el puerto directo. No existe borrar: una categoría con gastos se desactiva.
 */
@RestController
@RequestMapping("/api/categorias-gasto")
@RequiredArgsConstructor
class CategoriaGastoController {

    private final RegistrarCategoriaGasto registrarCategoria;
    private final ActualizarCategoriaGasto actualizarCategoria;
    private final DesactivarCategoriaGasto desactivarCategoria;
    private final RepositorioCategoriasGasto categorias;

    /** Todas, activas y desactivadas: el filtro de gastos viejos también las nombra. */
    @GetMapping
    List<RespuestaCategoria> todas() {
        return categorias.todas().stream().map(RespuestaCategoria::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RespuestaCategoria crear(@RequestBody PeticionCategoria peticion, @ActorActual Actor actor) {
        return RespuestaCategoria.de(registrarCategoria.ejecutar(peticion.nombre(), peticion.naturaleza(),
                peticion.mensual(), actor));
    }

    /** {@code PUT}: reemplaza el nombre y si se paga cada mes; repetirlo deja el mismo estado. */
    @PutMapping("/{id}")
    RespuestaCategoria actualizar(@PathVariable UUID id, @RequestBody PeticionCategoria peticion,
                                  @ActorActual Actor actor) {
        return RespuestaCategoria.de(actualizarCategoria.ejecutar(id, peticion.nombre(), peticion.mensual(), actor));
    }

    @PostMapping("/{id}/desactivacion")
    RespuestaCategoria desactivar(@PathVariable UUID id, @ActorActual Actor actor) {
        return RespuestaCategoria.de(desactivarCategoria.ejecutar(id, actor));
    }

    /** {@code naturaleza} solo al crear: después no cambia. {@code mensual}: si sus gastos suelen ser del mes. */
    record PeticionCategoria(String nombre, NaturalezaGasto naturaleza, boolean mensual) {
    }

    record RespuestaCategoria(UUID id, String nombre, NaturalezaGasto naturaleza, boolean activa, boolean mensual) {
        static RespuestaCategoria de(CategoriaGasto c) {
            return new RespuestaCategoria(c.getId(), c.getNombre(), c.getNaturaleza(), c.isActiva(), c.isMensual());
        }
    }
}
