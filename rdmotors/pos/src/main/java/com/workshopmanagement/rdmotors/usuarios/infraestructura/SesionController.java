package com.workshopmanagement.rdmotors.usuarios.infraestructura;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.workshopmanagement.rdmotors.compartido.dominio.Rol;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.ActorActual;
import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.TokenDeSesion;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.CambiarContrasena;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.Entrar;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.Sesion;
import com.workshopmanagement.rdmotors.usuarios.dominio.DatosDelEquipo;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — entrar, salir, quién soy y cambiar la contraseña (spec 0004, fase 1).
 *
 * <p>La respuesta de entrar <b>no trae el token</b>: lo pone en la cookie {@code HttpOnly}, que la página no puede
 * leer. El cuerpo solo dice quién entró.
 */
@RestController
@RequestMapping("/api/sesion")
@RequiredArgsConstructor
class SesionController {

    private final Entrar entrar;
    private final CambiarContrasena cambiarContrasena;
    private final TokenDeSesion token;

    @PostMapping
    ResponseEntity<RespuestaSesion> entrar(@RequestBody PeticionEntrar peticion, HttpServletRequest peticionHttp) {
        return conCookie(entrar.ejecutar(peticion.usuario(), peticion.contrasena(), equipoDe(peticionHttp)),
                HttpStatus.OK);
    }

    /**
     * Desde qué equipo se intentó entrar (spec 0004, RF-025): su dirección en la red de la tienda y lo que el
     * navegador dice ser. Se toma de la conexión, no de un encabezado que cualquiera puede escribir.
     */
    private static DatosDelEquipo equipoDe(HttpServletRequest peticion) {
        return new DatosDelEquipo(peticion.getRemoteAddr(), peticion.getHeader(HttpHeaders.USER_AGENT));
    }

    @GetMapping
    RespuestaSesion quienSoy(@ActorActual Sesion sesion) {
        return RespuestaSesion.de(sesion);
    }

    /** Pública: sirve aunque la sesión ya no valga. Borra la cookie. */
    @DeleteMapping
    ResponseEntity<Void> salir() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, token.borrar().toString()).build();
    }

    /** Sube la versión de la sesión: la cookie vieja deja de valer y sale una nueva. */
    @PutMapping("/contrasena")
    ResponseEntity<RespuestaSesion> cambiarContrasena(@ActorActual Sesion sesion,
                                                      @RequestBody PeticionContrasena peticion) {
        return conCookie(cambiarContrasena.ejecutar(sesion.actor(), peticion.actual(), peticion.nueva()),
                HttpStatus.OK);
    }

    private ResponseEntity<RespuestaSesion> conCookie(Sesion sesion, HttpStatus estado) {
        return ResponseEntity.status(estado).header(HttpHeaders.SET_COOKIE, token.cookie(sesion).toString())
                .body(RespuestaSesion.de(sesion));
    }

    record PeticionEntrar(String usuario, String contrasena) {
    }

    record PeticionContrasena(String actual, String nueva) {
    }

    /** Quién está adentro. {@code veCostos} le dice a la pantalla qué columnas mostrar (spec 0004, decisión 1). */
    record RespuestaSesion(UUID id, String usuario, String nombre, Rol rol, boolean veCostos,
                           boolean debeCambiarContrasena) {
        static RespuestaSesion de(Sesion s) {
            return new RespuestaSesion(s.actor().id(), s.usuario(), s.actor().nombre(), s.actor().rol(),
                    s.actor().rol().veCostos(), s.debeCambiarContrasena());
        }
    }
}
