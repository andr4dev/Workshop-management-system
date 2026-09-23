package com.workshopmanagement.rdmotors.usuarios.aplicacion;

import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;

/** CASO DE USO — volver a activar a alguien (spec 0004, RF-015): entra con su misma contraseña. */
@Transactional
public class ActivarUsuario {

    private final RepositorioUsuarios usuarios;
    private final RepositorioAuditoria auditoria;
    private final Reloj reloj;

    public ActivarUsuario(RepositorioUsuarios usuarios, RepositorioAuditoria auditoria, Reloj reloj) {
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public FichaUsuario ejecutar(UUID usuarioId, Actor actor) {
        actor.exigirAdministrador();
        Usuario usuario = usuarios.buscarParaModificar(usuarioId)
                .orElseThrow(() -> new ReglaDeNegocioException("El usuario no existe"));
        Map<String, Object> antes = usuario.fotografia();
        if (usuario.activar()) {
            usuarios.guardar(usuario);
            auditoria.registrar(EventoAuditoria.nuevo(reloj.ahora(), actor.id(), AccionAuditada.ACTIVAR_USUARIO,
                    Usuario.TIPO_AUDITORIA, usuario.getId(), antes, usuario.fotografia(), null));
        }
        return FichaUsuario.de(usuario);
    }
}
