package com.workshopmanagement.rdmotors.usuarios.infraestructura;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.Pagina;
import com.workshopmanagement.rdmotors.compartido.dominio.Rol;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.ActorActual;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.ActivarUsuario;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.CambiarRol;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.ConsultarEntradas;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.ConsultarEntradas.FichaEntrada;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.ConsultarUsuarios;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.CrearUsuario;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.DesactivarUsuario;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.FichaUsuario;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.RestablecerContrasena;

import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — *Usuarios*, del administrador (spec 0004, fase 4). Todo lo decide el caso de uso: quién puede,
 * que quede un administrador, que nadie se desactive a sí mismo. Sin borrar: se desactiva.
 */
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
class UsuarioController {

    private final ConsultarUsuarios consultarUsuarios;
    private final CrearUsuario crearUsuario;
    private final CambiarRol cambiarRol;
    private final DesactivarUsuario desactivarUsuario;
    private final ActivarUsuario activarUsuario;
    private final RestablecerContrasena restablecerContrasena;
    private final ConsultarEntradas consultarEntradas;

    @GetMapping
    List<FichaUsuario> todos(@ActorActual Actor actor) {
        return consultarUsuarios.todos(actor);
    }

    /**
     * El registro de entradas (spec 0004, RF-025): quién entró, cuándo, desde qué equipo, y los intentos que
     * fallaron. Va antes que {@code /{id}} para que "entradas" no se lea como un id.
     */
    @GetMapping("/entradas")
    RespuestaEntradas entradas(@RequestParam(defaultValue = "0") int pagina,
                               @RequestParam(defaultValue = "50") int tamano,
                               @ActorActual Actor actor) {
        Pagina<FichaEntrada> p = consultarEntradas.ultimas(pagina, tamano, actor);
        return new RespuestaEntradas(p.elementos(), p.total(), p.numero(), p.tamano(), p.totalPaginas());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    FichaUsuario crear(@RequestBody PeticionUsuario peticion, @ActorActual Actor actor) {
        return crearUsuario.ejecutar(peticion.nombre(), peticion.usuario(), peticion.rol(), peticion.contrasena(),
                actor);
    }

    /** {@code PUT}: repetir el mismo rol deja el mismo estado. */
    @PutMapping("/{id}/rol")
    FichaUsuario rol(@PathVariable UUID id, @RequestBody PeticionRol peticion, @ActorActual Actor actor) {
        return cambiarRol.ejecutar(id, peticion.rol(), actor);
    }

    @PostMapping("/{id}/desactivacion")
    FichaUsuario desactivar(@PathVariable UUID id, @ActorActual Actor actor) {
        return desactivarUsuario.ejecutar(id, actor);
    }

    @PostMapping("/{id}/activacion")
    FichaUsuario activar(@PathVariable UUID id, @ActorActual Actor actor) {
        return activarUsuario.ejecutar(id, actor);
    }

    /** La temporal viaja una sola vez, en esta respuesta: no se puede volver a consultar. */
    @PostMapping("/{id}/restablecimiento")
    RespuestaRestablecimiento restablecer(@PathVariable UUID id, @ActorActual Actor actor) {
        return new RespuestaRestablecimiento(restablecerContrasena.ejecutar(id, actor));
    }

    /** Sin validaciones de borde: las reglas y sus mensajes son del dominio. */
    record PeticionUsuario(String nombre, String usuario, Rol rol, String contrasena) {
    }

    record PeticionRol(Rol rol) {
    }

    record RespuestaRestablecimiento(String contrasenaTemporal) {
    }

    record RespuestaEntradas(List<FichaEntrada> elementos, long total, int numero, int tamano, int totalPaginas) {
    }
}
