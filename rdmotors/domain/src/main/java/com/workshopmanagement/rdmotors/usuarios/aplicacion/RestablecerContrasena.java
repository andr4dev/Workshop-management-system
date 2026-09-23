package com.workshopmanagement.rdmotors.usuarios.aplicacion;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.usuarios.dominio.ContrasenaTemporal;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.Contrasenas;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;

/**
 * CASO DE USO — el administrador le restablece la contraseña a alguien que la olvidó (spec 0004, RF-017).
 *
 * <p>Devuelve la temporal <b>una sola vez</b>, para dictársela: se guarda solo su hash. Queda como inicial (la cambia
 * al entrar) y las sesiones que tenía abiertas dejan de valer.
 */
@Transactional
public class RestablecerContrasena {

    private final RepositorioUsuarios usuarios;
    private final Contrasenas contrasenas;
    private final RepositorioAuditoria auditoria;
    private final Reloj reloj;

    public RestablecerContrasena(RepositorioUsuarios usuarios, Contrasenas contrasenas, RepositorioAuditoria auditoria,
                                 Reloj reloj) {
        this.usuarios = usuarios;
        this.contrasenas = contrasenas;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /** @return la contraseña temporal, en claro: la única vez que existe fuera de la cabeza de alguien */
    public String ejecutar(UUID usuarioId, Actor actor) {
        actor.exigirAdministrador();
        Usuario usuario = usuarios.buscarParaModificar(usuarioId)
                .orElseThrow(() -> new ReglaDeNegocioException("El usuario no existe"));
        String temporal = ContrasenaTemporal.nueva();
        usuario.restablecerContrasena(contrasenas.hash(temporal));
        usuarios.guardar(usuario);
        auditoria.registrar(EventoAuditoria.nuevo(reloj.ahora(), actor.id(), AccionAuditada.RESTABLECER_CONTRASENA,
                Usuario.TIPO_AUDITORIA, usuario.getId(), null, usuario.fotografia(), null));
        return temporal;
    }
}
