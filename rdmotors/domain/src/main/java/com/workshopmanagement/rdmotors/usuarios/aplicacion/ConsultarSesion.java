package com.workshopmanagement.rdmotors.usuarios.aplicacion;

import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;

/**
 * CASO DE USO — ¿la sesión que trae este token sigue valiendo? (spec 0004, RF-015).
 *
 * <p>Un token firmado vale hasta que vence. Para que desactivar a alguien o restablecerle la contraseña lo saque
 * <b>en el acto</b>, cada petición pasa por aquí: una lectura por llave primaria. El rol también sale de la base, no
 * del token: cambiarle el rol a alguien aplica en su siguiente petición.
 */
@Transactional(readOnly = true)
public class ConsultarSesion {

    private final RepositorioUsuarios usuarios;

    public ConsultarSesion(RepositorioUsuarios usuarios) {
        this.usuarios = usuarios;
    }

    /** Vacío si el usuario no existe, está desactivado o la versión del token ya no es la suya. */
    public Optional<Sesion> vigente(UUID usuarioId, long versionDelToken) {
        return usuarios.buscar(usuarioId)
                .filter(u -> u.isActivo() && u.getVersionSesion() == versionDelToken)
                .map(Sesion::de);
    }
}
