package com.workshopmanagement.rdmotors.compras.infraestructura;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.ActorActual;
import com.workshopmanagement.rdmotors.compras.aplicacion.DesactivarCuenta;
import com.workshopmanagement.rdmotors.compras.aplicacion.RegistrarCuenta;
import com.workshopmanagement.rdmotors.compras.dominio.CuentaPago;
import com.workshopmanagement.rdmotors.compras.dominio.puerto.RepositorioCuentas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — cuentas desde las que se transfiere.
 *
 * <p>Misma asimetría que proveedores: crear pasa por el caso de uso (tiene la regla del nombre
 * repetido); listar usa el puerto directo. Crear y desactivar son del administrador; la lista la puede leer
 * cualquiera que haya entrado: son nombres como "Nequi", y el formulario de gastos del cajero la carga.
 */
@RestController
@RequestMapping("/api/cuentas")
@RequiredArgsConstructor
class CuentaController {

    private final RegistrarCuenta registrarCuenta;
    private final DesactivarCuenta desactivarCuenta;
    private final RepositorioCuentas cuentas;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RespuestaCuenta crear(@Valid @RequestBody PeticionCuenta peticion, @ActorActual Actor actor) {
        return RespuestaCuenta.de(registrarCuenta.ejecutar(peticion.nombre(), actor));
    }

    /**
     * Dar de baja una cuenta (spec 0002, RF-004). No existe endpoint para borrar: una cuenta con
     * compras se desactiva y las compras la siguen nombrando.
     */
    @PostMapping("/{id}/desactivacion")
    RespuestaCuenta desactivar(@PathVariable UUID id, @ActorActual Actor actor) {
        return RespuestaCuenta.de(desactivarCuenta.ejecutar(id, actor));
    }

    /** Solo las activas: una desactivada ya no se ofrece para compras nuevas. */
    @GetMapping
    List<RespuestaCuenta> activas() {
        return cuentas.activas().stream().map(RespuestaCuenta::de).toList();
    }

    record PeticionCuenta(@NotBlank String nombre) {
    }

    record RespuestaCuenta(UUID id, String nombre, boolean activa) {
        static RespuestaCuenta de(CuentaPago c) {
            return new RespuestaCuenta(c.getId(), c.getNombre(), c.isActiva());
        }
    }
}
