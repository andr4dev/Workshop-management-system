package com.workshopmanagement.rdmotors.usuarios.aplicacion;

import java.util.Objects;

import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;

/**
 * Quién está adentro (spec 0004): la persona con su rol, el usuario con que entró, la versión de su sesión (la que
 * lleva el token) y si todavía tiene que cambiar la contraseña.
 */
public record Sesion(Actor actor, String usuario, long versionSesion, boolean debeCambiarContrasena) {

    public Sesion {
        Objects.requireNonNull(actor, "actor");
    }

    public static Sesion de(Usuario usuario) {
        return new Sesion(usuario.actor(), usuario.getUsuario(), usuario.getVersionSesion(),
                usuario.isDebeCambiarContrasena());
    }
}
