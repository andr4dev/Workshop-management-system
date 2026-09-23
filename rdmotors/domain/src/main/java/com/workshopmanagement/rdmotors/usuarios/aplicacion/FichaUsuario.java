package com.workshopmanagement.rdmotors.usuarios.aplicacion;

import java.time.Instant;
import java.util.UUID;

import com.workshopmanagement.rdmotors.compartido.dominio.Rol;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;

/**
 * Una persona como la ve el administrador en *Usuarios* (spec 0004, RF-024): sin la contraseña ni sus intentos.
 *
 * @param ultimaEntrada {@code null} si nunca ha entrado
 */
public record FichaUsuario(UUID id, String usuario, String nombre, Rol rol, boolean activo,
                           boolean debeCambiarContrasena, Instant ultimaEntrada, Instant creadoEn) {

    public static FichaUsuario de(Usuario u) {
        return new FichaUsuario(u.getId(), u.getUsuario(), u.getNombre(), u.getRol(), u.isActivo(),
                u.isDebeCambiarContrasena(), u.getUltimaEntrada(), u.getCreadoEn());
    }
}
