package com.workshopmanagement.rdmotors.usuarios.aplicacion;

import java.time.Instant;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.Actor;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.ReglaDeNegocioException;
import com.workshopmanagement.rdmotors.compartido.dominio.Rol;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.Contrasenas;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;

/**
 * CASO DE USO — el administrador crea a alguien (spec 0004, RF-013): nombre (el del comprobante), usuario, rol y
 * contraseña inicial. La inicial la escogió otro, así que al entrar la cambia (RF-014).
 *
 * <p>Un usuario repetido, sin distinguir mayúsculas ni tildes, se rechaza aquí con su nombre; si dos lo crean a la
 * vez, lo ataja el índice único de la base con el mismo mensaje.
 */
@Transactional
public class CrearUsuario {

    private final RepositorioUsuarios usuarios;
    private final Contrasenas contrasenas;
    private final RepositorioAuditoria auditoria;
    private final Reloj reloj;

    public CrearUsuario(RepositorioUsuarios usuarios, Contrasenas contrasenas, RepositorioAuditoria auditoria,
                        Reloj reloj) {
        this.usuarios = usuarios;
        this.contrasenas = contrasenas;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public FichaUsuario ejecutar(String nombre, String usuario, Rol rol, String contrasenaInicial, Actor actor) {
        actor.exigirAdministrador();
        Usuario.exigirContrasenaValida(contrasenaInicial);
        Instant ahora = reloj.ahora();
        Usuario nuevo = Usuario.nuevo(usuario, nombre, rol, contrasenas.hash(contrasenaInicial), true, ahora);
        usuarios.buscarPorUsuario(nuevo.getUsuario()).ifPresent(existente -> {
            throw new ReglaDeNegocioException("Ya existe el usuario «" + existente.getUsuario() + "»");
        });
        usuarios.guardar(nuevo);
        auditoria.registrar(EventoAuditoria.nuevo(ahora, actor.id(), AccionAuditada.CREAR_USUARIO,
                Usuario.TIPO_AUDITORIA, nuevo.getId(), null, nuevo.fotografia(), null));
        return FichaUsuario.de(nuevo);
    }
}
