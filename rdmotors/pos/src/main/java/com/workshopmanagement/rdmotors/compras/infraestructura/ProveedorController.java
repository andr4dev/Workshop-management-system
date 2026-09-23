package com.workshopmanagement.rdmotors.compras.infraestructura;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.ActorActual;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarProveedor;
import com.workshopmanagement.rdmotors.compras.dominio.Proveedor;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioProveedores;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — proveedores.
 *
 * <p>Fijate en la asimetria, que es deliberada: <b>crear</b> pasa por el caso de uso, porque tiene
 * regla y transaccion; <b>listar</b> usa el puerto directo. Un caso de uso que solo reenvia al
 * repositorio es ceremonia — ver la bitacora del plan 0001. Los dos son del administrador (spec 0004,
 * §5): listar exige el rol aquí mismo, con la regla del {@code Actor}, porque no hay caso de uso donde ponerla.
 */
@RestController
@RequestMapping("/api/proveedores")
@RequiredArgsConstructor
class ProveedorController {

    private final RegistrarProveedor registrarProveedor;
    private final RepositorioProveedores proveedores;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RespuestaProveedor crear(@Valid @RequestBody PeticionProveedor peticion, @ActorActual Actor actor) {
        Proveedor creado = registrarProveedor.ejecutar(
                peticion.nombre(), peticion.nit(), peticion.telefono(), actor);
        return RespuestaProveedor.de(creado);
    }

    @GetMapping
    List<RespuestaProveedor> activos(@ActorActual Actor actor) {
        actor.exigirAdministrador();
        return proveedores.activos().stream().map(RespuestaProveedor::de).toList();
    }

    /** Solo el nombre es obligatorio: exigir NIT para registrar una compra que ya ocurrio seria absurdo. */
    record PeticionProveedor(@NotBlank String nombre, String nit, String telefono) {
    }

    record RespuestaProveedor(UUID id, String nombre, String nit, String telefono) {
        static RespuestaProveedor de(Proveedor p) {
            return new RespuestaProveedor(p.getId(), p.getNombre(), p.getNit(), p.getTelefono());
        }
    }
}
