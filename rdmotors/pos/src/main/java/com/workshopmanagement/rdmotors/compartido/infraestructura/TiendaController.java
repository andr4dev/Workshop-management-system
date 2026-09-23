package com.workshopmanagement.rdmotors.compartido.infraestructura;

import org.springframework.web.bind.annotation.*;

import com.workshopmanagement.rdmotors.compartido.aplicacion.ActualizarDatosTienda;
import com.workshopmanagement.rdmotors.compartido.aplicacion.ActualizarDatosTienda.ComandoDatosTienda;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.DatosTienda;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioDatosTienda;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.ActorActual;

import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — los datos de la tienda que encabezan el comprobante (spec 0003, RF-033).
 *
 * <p>{@code PUT} y no {@code PATCH}: reemplaza todo, así que repetirlo deja el mismo estado. Leer usa el
 * puerto directo; cambiar pasa por el caso de uso, que tiene las reglas.
 */
@RestController
@RequestMapping("/api/tienda")
@RequiredArgsConstructor
class TiendaController {

    private final ActualizarDatosTienda actualizarDatosTienda;
    private final RepositorioDatosTienda tienda;

    @GetMapping
    RespuestaTienda obtener() {
        return RespuestaTienda.de(tienda.actuales());
    }

    /** Del administrador (spec 0004, §5). Leerlos no: el cajero imprime el comprobante con ellos. */
    @PutMapping
    RespuestaTienda actualizar(@RequestBody PeticionTienda peticion, @ActorActual Actor actor) {
        return RespuestaTienda.de(actualizarDatosTienda.ejecutar(new ComandoDatosTienda(
                peticion.nombreComercial(), peticion.nit(), peticion.direccion(), peticion.telefono(),
                peticion.mensajePie()), actor));
    }

    /** Sin validaciones de borde: las reglas (obligatorio, largos) y sus mensajes son del dominio. */
    record PeticionTienda(String nombreComercial, String nit, String direccion, String telefono,
                          String mensajePie) {
    }

    record RespuestaTienda(String nombreComercial, String nit, String direccion, String telefono,
                           String mensajePie) {

        static RespuestaTienda de(DatosTienda d) {
            return new RespuestaTienda(d.getNombreComercial(), d.getNit(), d.getDireccion(), d.getTelefono(),
                    d.getMensajePie());
        }
    }
}
