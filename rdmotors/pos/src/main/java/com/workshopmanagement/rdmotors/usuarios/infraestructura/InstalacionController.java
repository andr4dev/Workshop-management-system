package com.workshopmanagement.rdmotors.usuarios.infraestructura;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.workshopmanagement.rdmotors.compartido.infraestructura.seguridad.TokenDeSesion;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.CrearPrimerAdministrador;
import com.workshopmanagement.rdmotors.usuarios.aplicacion.Sesion;

import lombok.RequiredArgsConstructor;

/**
 * ADAPTADOR DE ENTRADA — el primer administrador (spec 0004, H6 y RF-018). Público: al instalar no hay nadie que
 * pueda entrar. Con un usuario creado, crear otro por aquí responde 409 y no crea nada.
 */
@RestController
@RequestMapping("/api/instalacion")
@RequiredArgsConstructor
class InstalacionController {

    private final CrearPrimerAdministrador crearPrimerAdministrador;
    private final TokenDeSesion token;

    @GetMapping
    RespuestaInstalacion estado() {
        return new RespuestaInstalacion(crearPrimerAdministrador.faltaAdministrador());
    }

    /** Crea al administrador y lo deja adentro. */
    @PostMapping("/administrador")
    ResponseEntity<SesionController.RespuestaSesion> crearAdministrador(@RequestBody PeticionAdministrador peticion) {
        Sesion sesion = crearPrimerAdministrador.ejecutar(peticion.nombre(), peticion.usuario(), peticion.contrasena());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, token.cookie(sesion).toString())
                .body(SesionController.RespuestaSesion.de(sesion));
    }

    record RespuestaInstalacion(boolean faltaAdministrador) {
    }

    record PeticionAdministrador(String nombre, String usuario, String contrasena) {
    }
}
