package com.workshopmanagement.rdmotors.usuarios.aplicacion;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.Contrasenas;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;

/**
 * CASO DE USO — cada uno cambia su contraseña, dando la actual (spec 0004, H8, RF-014 y RF-017). También es como se
 * cambia la inicial o la restablecida, obligatorio antes de hacer cualquier otra cosa.
 *
 * <p>Sube la versión de la sesión: los tokens que tenía la persona dejan de valer. Devuelve la sesión nueva, para que
 * quien la cambió siga adentro con un token nuevo.
 */
@Transactional
public class CambiarContrasena {

    private final RepositorioUsuarios usuarios;
    private final Contrasenas contrasenas;

    public CambiarContrasena(RepositorioUsuarios usuarios, Contrasenas contrasenas) {
        this.usuarios = usuarios;
        this.contrasenas = contrasenas;
    }

    public Sesion ejecutar(Actor actor, String actual, String nueva) {
        Usuario quien = usuarios.buscarParaModificar(actor.id())
                .orElseThrow(() -> new ReglaDeNegocioException("El usuario no existe"));
        if (actual == null || !contrasenas.coincide(actual, quien.getHash())) {
            throw new ReglaDeNegocioException("La contraseña actual no es esa");
        }
        Usuario.exigirContrasenaValida(nueva);
        if (nueva.equals(actual)) {
            throw new ReglaDeNegocioException("La contraseña nueva tiene que ser distinta de la actual");
        }
        quien.cambiarContrasena(contrasenas.hash(nueva));
        usuarios.guardar(quien);
        return Sesion.de(quien);
    }
}
