package com.workshopmanagement.rdmotors.usuarios.aplicacion;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.Rol;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;

/**
 * CASO DE USO — pasar a alguien de cajero a administrador, o al revés (spec 0004, RF-016 y RF-020).
 *
 * <p><b>Siempre queda un administrador activo.</b> Se revisa bajo candado: dos administradores que se quitan el rol
 * uno al otro a la vez no pueden dejar la tienda sin administrador. Repetir el mismo rol no es un error y no queda
 * en la auditoría.
 */
@Transactional
public class CambiarRol {

    public static final String ULTIMO_ADMINISTRADOR = "Tiene que quedar al menos un administrador activo";

    private final RepositorioUsuarios usuarios;
    private final RepositorioAuditoria auditoria;
    private final Reloj reloj;

    public CambiarRol(RepositorioUsuarios usuarios, RepositorioAuditoria auditoria, Reloj reloj) {
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public FichaUsuario ejecutar(UUID usuarioId, Rol nuevo, Actor actor) {
        actor.exigirAdministrador();
        usuarios.bloquearCambiosDeAdministradores();
        Usuario usuario = usuarios.buscarParaModificar(usuarioId)
                .orElseThrow(() -> new ReglaDeNegocioException("El usuario no existe"));
        if (usuario.esAdministradorActivo() && nuevo == Rol.CAJERO && usuarios.administradoresActivos() <= 1) {
            throw new ReglaDeNegocioException(ULTIMO_ADMINISTRADOR);
        }
        Map<String, Object> antes = usuario.fotografia();
        if (usuario.cambiarRol(nuevo)) {
            usuarios.guardar(usuario);
            Instant ahora = reloj.ahora();
            auditoria.registrar(EventoAuditoria.nuevo(ahora, actor.id(), AccionAuditada.CAMBIAR_ROL,
                    Usuario.TIPO_AUDITORIA, usuario.getId(), antes, usuario.fotografia(), null));
        }
        return FichaUsuario.de(usuario);
    }
}
