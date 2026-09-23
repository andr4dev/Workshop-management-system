package com.workshopmanagement.rdmotors.usuarios.aplicacion;

import java.time.Instant;

import org.springframework.transaction.annotation.Transactional;

import com.workshopmanagement.rdmotors.compartido.dominio.AccionAuditada;
import com.workshopmanagement.rdmotors.compartido.dominio.EventoAuditoria;
import com.workshopmanagement.rdmotors.compartido.dominio.Rol;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.Reloj;
import com.workshopmanagement.rdmotors.compartido.dominio.puerto.RepositorioAuditoria;
import com.workshopmanagement.rdmotors.usuarios.dominio.Usuario;
import com.workshopmanagement.rdmotors.usuarios.dominio.YaHayUsuariosException;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.Contrasenas;
import com.workshopmanagement.rdmotors.usuarios.dominio.puerto.RepositorioUsuarios;

/**
 * CASO DE USO — el primer administrador, al instalar (spec 0004, H6 y RF-018).
 *
 * <p>Solo mientras no exista ningún usuario. <b>Bajo candado</b>: dos pantallas que lo crean a la vez no dejan dos
 * administradores. No hay una contraseña por defecto que se olvide cambiar (decisión 4): la escoge quien instala, y
 * por eso no tiene que cambiarla al entrar.
 */
@Transactional
public class CrearPrimerAdministrador {

    public static final String TIPO_AUDITORIA = Usuario.TIPO_AUDITORIA;

    private final RepositorioUsuarios usuarios;
    private final Contrasenas contrasenas;
    private final RepositorioAuditoria auditoria;
    private final Reloj reloj;

    public CrearPrimerAdministrador(RepositorioUsuarios usuarios, Contrasenas contrasenas,
                                    RepositorioAuditoria auditoria, Reloj reloj) {
        this.usuarios = usuarios;
        this.contrasenas = contrasenas;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /** Si todavía hay que crearlo: la pantalla de entrar lo ofrece solo entonces. */
    @Transactional(readOnly = true)
    public boolean faltaAdministrador() {
        return !usuarios.hayUsuarios();
    }

    /** @return la sesión del administrador recién creado: queda adentro */
    public Sesion ejecutar(String nombre, String usuario, String contrasena) {
        usuarios.bloquearAltaDelPrimero();
        if (usuarios.hayUsuarios()) {
            throw new YaHayUsuariosException();
        }
        Usuario.exigirContrasenaValida(contrasena);
        Instant ahora = reloj.ahora();
        Usuario administrador = Usuario.nuevo(usuario, nombre, Rol.ADMINISTRADOR, contrasenas.hash(contrasena),
                false, ahora);
        administrador.entroBien(ahora);
        usuarios.guardar(administrador);
        auditoria.registrar(EventoAuditoria.nuevo(ahora, administrador.getId(), AccionAuditada.CREAR_USUARIO,
                TIPO_AUDITORIA, administrador.getId(), null, administrador.fotografia(), "Primer administrador"));
        return Sesion.de(administrador);
    }
}
