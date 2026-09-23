package com.workshopmanagement.rdmotors.compartido.dominio;

import java.util.Objects;
import java.util.UUID;

/**
 * Quién está haciendo algo, y con qué rol (spec 0004). Lo arma la seguridad con cada petición, a partir de la
 * sesión: **el navegador no lo decide**.
 *
 * <p>Es un valor, no la entidad del usuario: los casos de uso lo necesitan para saber a nombre de quién queda algo
 * y qué puede hacer, no su contraseña ni sus intentos fallidos.
 *
 * @param nombre el que se muestra: "Carolina", el del comprobante
 */
public record Actor(UUID id, String nombre, Rol rol) {

    public Actor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nombre, "nombre");
        Objects.requireNonNull(rol, "rol");
    }

    public boolean esAdministrador() {
        return rol == Rol.ADMINISTRADOR;
    }

    /** El bloqueo de lo que es del administrador va aquí, en el caso de uso, no en la pantalla (spec 0004, RF-010). */
    public void exigirAdministrador() {
        if (!esAdministrador()) {
            throw new NoPermitidoException();
        }
    }
}
