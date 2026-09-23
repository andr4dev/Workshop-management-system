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

/**
 * CASO DE USO — desactivar a alguien (spec 0004, RF-015 y RF-016). No se borra: lo que hizo lo sigue nombrando. No
 * puede entrar, y su sesión abierta se cae en su próxima acción. Si tenía un turno abierto, sigue abierto: lo cierra
 * un administrador.
 *
 * <p>Nadie se desactiva a sí mismo, y siempre queda un administrador activo (bajo el mismo candado que el cambio de
 * rol).
 */
@Transactional
public class DesactivarUsuario {

    public static final String A_SI_MISMO = "No puedes desactivarte a ti mismo";

    private final RepositorioUsuarios usuarios;
    private final RepositorioAuditoria auditoria;
    private final Reloj reloj;

    public DesactivarUsuario(RepositorioUsuarios usuarios, RepositorioAuditoria auditoria, Reloj reloj) {
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public FichaUsuario ejecutar(UUID usuarioId, Actor actor) {
        actor.exigirAdministrador();
        if (actor.id().equals(usuarioId)) {
            throw new ReglaDeNegocioException(A_SI_MISMO);
        }
        usuarios.bloquearCambiosDeAdministradores();
        Usuario usuario = usuarios.buscarParaModificar(usuarioId)
                .orElseThrow(() -> new ReglaDeNegocioException("El usuario no existe"));
        if (usuario.esAdministradorActivo() && usuarios.administradoresActivos() <= 1) {
            throw new ReglaDeNegocioException(CambiarRol.ULTIMO_ADMINISTRADOR);
        }
        Map<String, Object> antes = usuario.fotografia();
        if (usuario.desactivar()) {
            usuarios.guardar(usuario);
            auditoria.registrar(EventoAuditoria.nuevo(reloj.ahora(), actor.id(), AccionAuditada.DESACTIVAR_USUARIO,
                    Usuario.TIPO_AUDITORIA, usuario.getId(), antes, usuario.fotografia(), null));
        }
        return FichaUsuario.de(usuario);
    }
}
